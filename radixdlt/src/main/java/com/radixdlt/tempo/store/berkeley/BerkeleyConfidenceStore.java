package com.radixdlt.tempo.store.berkeley;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.common.AID;
import com.radixdlt.tempo.Resource;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.store.ConfidenceStore;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.Longs;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import org.radix.database.DatabaseEnvironment;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Singleton
public final class BerkeleyConfidenceStore implements Resource, ConfidenceStore {
	private static final String CONFIDENCE_STORE_NAME = "tempo2.consensus.confidence";
	private static final Logger logger = Logging.getLogger("store.confidence");

	private final DatabaseEnvironment dbEnv;
	private Database confidence;

	@Inject
	public BerkeleyConfidenceStore(DatabaseEnvironment dbEnv) {
		this.dbEnv = Objects.requireNonNull(dbEnv, "dbEnv is required");
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
			this.confidence = dbEnv.openDatabase(null, CONFIDENCE_STORE_NAME, primaryConfig);
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
			env.truncateDatabase(transaction, CONFIDENCE_STORE_NAME, false);
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
		if (this.confidence != null) {
			this.confidence.close();
		}
	}

	@Override
	public int increaseConfidence(AID aid) {
		Transaction transaction = dbEnv.getEnvironment().beginTransaction(null, null);
		try {
			DatabaseEntry key = new DatabaseEntry(toPKey(aid));
			DatabaseEntry value = new DatabaseEntry();

			// TODO potential race condition here?
			OperationStatus status = this.confidence.get(transaction, key, value, LockMode.DEFAULT);
			int previousConfidence = 0;
			if (status != OperationStatus.NOTFOUND && status != OperationStatus.SUCCESS) {
				fail("Database returned status " + status + " for get operation");
			} else {
				previousConfidence = Ints.fromByteArray(value.getData());
			}
			int nextConfidence = previousConfidence + 1;
			value.setData(Longs.toByteArray(nextConfidence));
			this.confidence.put(transaction, key, value);
			transaction.commit();
			return nextConfidence;
		} catch (Exception e) {
			transaction.abort();
			fail("Error while getting cursor for '" + aid + "'", e);
		}
		throw new IllegalStateException("Should never reach here");
	}

	@Override
	public boolean delete(AID aid) {
		Transaction transaction = dbEnv.getEnvironment().beginTransaction(null, null);
		try {
			DatabaseEntry key = new DatabaseEntry(toPKey(aid));
			OperationStatus status = this.confidence.delete(transaction, key);
			transaction.commit();
			return status == OperationStatus.SUCCESS;
		} catch (Exception e) {
			transaction.abort();
			fail("Error while getting cursor for '" + aid + "'", e);
		}
		throw new IllegalStateException("Should never reach here");
	}

	private byte[] toPKey(AID aid) {
		return aid.getBytes();
	}
}
