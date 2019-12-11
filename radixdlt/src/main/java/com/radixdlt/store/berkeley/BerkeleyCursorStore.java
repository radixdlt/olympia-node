package com.radixdlt.store.berkeley;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.common.EUID;
import com.radixdlt.consensus.tempo.TempoException;
import com.radixdlt.store.CursorStore;
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
import java.util.Optional;
import java.util.OptionalLong;

@Singleton
public final class BerkeleyCursorStore implements CursorStore {
	private static final String LC_CURSOR_STORE_NAME = "tempo2.sync.iterative.cursors";
	private static final Logger logger = Logging.getLogger("store.cursors");

	private final DatabaseEnvironment dbEnv;
	private Database cursors;

	@Inject
	public BerkeleyCursorStore(DatabaseEnvironment dbEnv) {
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

	private void open() {
		DatabaseConfig primaryConfig = new DatabaseConfig();
		primaryConfig.setAllowCreate(true);
		primaryConfig.setTransactional(true);

		try {
			Environment dbEnv = this.dbEnv.getEnvironment();
			this.cursors = dbEnv.openDatabase(null, LC_CURSOR_STORE_NAME, primaryConfig);
		} catch (Exception e) {
			throw new TempoException("Error while opening database", e);
		}

		if (System.getProperty("db.check_integrity", "1").equals("1")) {
			// TODO implement intergrity check
		}
	}

	@Override
	public void reset() {
		dbEnv.withLock(() -> {
			Transaction transaction = null;
			try {
				Environment env = this.dbEnv.getEnvironment();
				transaction = env.beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
				env.truncateDatabase(transaction, LC_CURSOR_STORE_NAME, false);
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
			}
		});
	}

	@Override
	public void close() {
		if (this.cursors != null) {
			this.cursors.close();
		}
	}

	@Override
	public void put(EUID nid, long cursor) {
		Transaction transaction = dbEnv.getEnvironment().beginTransaction(null, null);
		try {
			DatabaseEntry key = new DatabaseEntry(toPKey(nid));
			DatabaseEntry value = new DatabaseEntry(Longs.toByteArray(cursor));

			OperationStatus status = this.cursors.put(transaction, key, value);
			if (status != OperationStatus.SUCCESS) {
				fail("Database returned status " + status + " for put operation");
			}

			transaction.commit();
		} catch (Exception e) {
			transaction.abort();
			fail("Error while storing cursor for '" + nid + "'", e);
		}
	}

	@Override
	public OptionalLong get(EUID nid) {
		try {
			DatabaseEntry key = new DatabaseEntry(toPKey(nid));
			DatabaseEntry value = new DatabaseEntry();

			OperationStatus status = this.cursors.get(null, key, value, LockMode.DEFAULT);
			if (status == OperationStatus.NOTFOUND) {
				return OptionalLong.empty();
			} else if (status != OperationStatus.SUCCESS) {
				fail("Database returned status " + status + " for get operation");
			} else {
				long cursor = Longs.fromByteArray(value.getData());
				return OptionalLong.of(cursor);
			}
		} catch (Exception e) {
			fail("Error while getting cursor for '" + nid + "'", e);
		}

		return OptionalLong.empty();
	}

	private byte[] toPKey(EUID nid) {
		return nid.toByteArray();
	}
}
