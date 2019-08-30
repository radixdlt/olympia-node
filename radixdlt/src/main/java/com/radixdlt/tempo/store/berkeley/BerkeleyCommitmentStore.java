package com.radixdlt.tempo.store.berkeley;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.store.CommitmentStore;
import com.radixdlt.tempo.store.Store;
import com.radixdlt.utils.Longs;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import org.bouncycastle.util.Arrays;
import org.radix.database.DatabaseEnvironment;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

@Singleton
public class BerkeleyCommitmentStore implements Store, CommitmentStore {
	private static final String COMMITMENTS_DB_NAME = "tempo2.sync.iterative.commitments";
	private static final Logger logger = Logging.getLogger("CursorStore");

	private final DatabaseEnvironment dbEnv;
	private Database commitments; // commitment hashes by NID + logical clock

	@Inject
	public BerkeleyCommitmentStore(DatabaseEnvironment dbEnv) {
		this.dbEnv = Objects.requireNonNull(dbEnv, "dbEnv is required");

		this.open();
	}

	private void fail(String message) {
		logger.error(message);
		throw new TempoException(message);
	}

	private void fail(String message, Exception cause) {
		logger.error(message, cause);
		throw new TempoException(message, cause);
	}

	@Override
	public void open() {
		DatabaseConfig primaryConfig = new DatabaseConfig();
		primaryConfig.setAllowCreate(true);
		primaryConfig.setTransactional(true);

		try {
			Environment dbEnv = this.dbEnv.getEnvironment();
			this.commitments = dbEnv.openDatabase(null, COMMITMENTS_DB_NAME, primaryConfig);
		} catch (Exception e) {
			throw new TempoException("Error while opening database", e);
		}

		if (System.getProperty("db.check_integrity", "1").equals("1")) {
			// TODO implement intergrity check
		}
	}

	@Override
	public void reset() {
		Transaction transaction = null;
		try {
			dbEnv.lock();

			Environment env = this.dbEnv.getEnvironment();
			transaction = env.beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
			env.truncateDatabase(transaction, COMMITMENTS_DB_NAME, false);
			transaction.commit();
		} catch (DatabaseNotFoundException e) {
			if (transaction != null) {
				transaction.abort();
			}
			logger.warn("Error while resetting database, database not found", e);
		} catch (Exception e) {
			if (transaction != null) {
				transaction.abort();
			}
			throw new TempoException("Error while resetting databases", e);
		} finally {
			dbEnv.unlock();
		}
	}

	@Override
	public void close() {
		if (this.commitments != null) {
			this.commitments.close();
		}
	}

	@Override
	public void put(EUID nid, long logicalClock, Hash commitment) {
		Transaction transaction = dbEnv.getEnvironment().beginTransaction(null, null);
		try {
			DatabaseEntry pKey = new DatabaseEntry(toPKey(nid, logicalClock));
			DatabaseEntry value = new DatabaseEntry(commitment.toByteArray());
			OperationStatus status = this.commitments.putNoOverwrite(transaction, pKey, value);
			if (status != OperationStatus.SUCCESS) {
				fail("Database returned status " + status + " for put operation");
			}

			transaction.commit();
		} catch (Exception e) {
			transaction.abort();
			fail("Error while storing commitment at '" + logicalClock + "' for '" + nid + "'", e);
		}
	}

	@Override
	public void put(EUID nid, List<Hash> commitments, long startPosition) {
		Transaction transaction = dbEnv.getEnvironment().beginTransaction(null, null);
		try {
			DatabaseEntry pKey = new DatabaseEntry();
			DatabaseEntry value = new DatabaseEntry();
			for (int i = 0; i < commitments.size(); i++) {
				pKey.setData(toPKey(nid, startPosition + i));
				value.setData(commitments.get(i).toByteArray());
				OperationStatus status = this.commitments.putNoOverwrite(transaction, pKey, value);
				if (status != OperationStatus.SUCCESS) {
					fail("Database returned status " + status + " for put operation");
				}
			}

			transaction.commit();
		} catch (Exception e) {
			transaction.abort();
			fail("Error while storing batched commitments for '" + nid + "'", e);
		}
	}

	@Override
	public ImmutableList<Hash> getNext(EUID nid, long logicalClock, int limit) {
		ImmutableList.Builder<Hash> commitments = ImmutableList.builder();
		try (Cursor cursor = this.commitments.openCursor(null, null)) {
			DatabaseEntry pKey = new DatabaseEntry(toPKey(nid, logicalClock + 1));
			DatabaseEntry value = new DatabaseEntry();

			OperationStatus status = cursor.getSearchKeyRange(pKey, value, LockMode.DEFAULT);
			int size = 0;
			while (status == OperationStatus.SUCCESS && size < limit) {
				EUID valueNid = getNidFromPKey(pKey.getData());
				// early out if the nid no longer matches (overran into other node's commitments)
				if (!valueNid.equals(nid)) {
					break;
				}

				Hash commitment = new Hash(value.getData());
				commitments.add(commitment);
				size++;
				status = cursor.getNext(pKey, value, LockMode.DEFAULT);
			}
		} catch (Exception e) {
			fail("Error while getting next commitments for '" + nid + "'", e);
		}

		return commitments.build();
	}

	@Override
	public ImmutableList<Hash> getLast(EUID nid, int limit) {
		LinkedList<Hash> commitments = new LinkedList<>();
		try (Cursor cursor = this.commitments.openCursor(null, null)) {
			DatabaseEntry pKey = new DatabaseEntry(nid.toByteArray());
			DatabaseEntry value = new DatabaseEntry();

			OperationStatus status = OperationStatus.NOTFOUND;
			if (cursor.getSearchKeyRange(pKey, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				if (cursor.getNextNoDup(pKey, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					status = cursor.getPrev(pKey, value, LockMode.DEFAULT);
				} else {
					status = cursor.getLast(pKey, value, LockMode.DEFAULT);
				}
			}

			while (status == OperationStatus.SUCCESS && commitments.size() < limit) {
				EUID valueNid = getNidFromPKey(pKey.getData());
				// early out if the nid no longer matches (overran into other node's commitments)
				if (!valueNid.equals(nid)) {
					break;
				}

				Hash commitment = new Hash(value.getData());
				commitments.addFirst(commitment);

				status = cursor.getNextDup(pKey, value, LockMode.DEFAULT);
			}
		} catch (Exception e) {
			fail("Error while getting last commitments for '" + nid + "'", e);
		}

		return ImmutableList.copyOf(commitments);
	}

	@Override
	public void delete(EUID nid) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	private byte[] toPKey(EUID nid, long logicalClock) {
		return Arrays.concatenate(nid.toByteArray(), Longs.toByteArray(logicalClock));
	}

	private long getPositionFromPKey(byte[] pKey) {
		return Longs.fromByteArray(pKey, EUID.BYTES);
	}

	private EUID getNidFromPKey(byte[] pKey) {
		return new EUID(pKey);
	}
}
