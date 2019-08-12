package com.radixdlt.tempo.store;

import com.radixdlt.common.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.tempo.Store;
import com.radixdlt.tempo.exceptions.TempoException;
import com.radixdlt.tempo.sync.IterativeCursor;
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
import java.util.function.Supplier;

public final class IterativeCursorStore implements Store {
	private static final Logger logger = Logging.getLogger("Sync.Store");

	private final Supplier<DatabaseEnvironment> dbEnv;
	private final Supplier<Serialization> serialization;
	private Database cursors;

	public IterativeCursorStore(Supplier<DatabaseEnvironment> dbEnv, Supplier<Serialization> serialization) {
		this.dbEnv = Objects.requireNonNull(dbEnv, "dbEnv is required");
		this.serialization = Objects.requireNonNull(serialization, "serialization is required");
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
			Environment dbEnv = this.dbEnv.get().getEnvironment();
			this.cursors = dbEnv.openDatabase(null, "tempo2.sync.iterative.cursors", primaryConfig);
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
			dbEnv.get().lock();

			Environment env = this.dbEnv.get().getEnvironment();
			transaction = env.beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
			env.truncateDatabase(transaction, "tempo2.sync.iterative.cursors", false);
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
			dbEnv.get().unlock();
		}
	}

	@Override
	public void close() {
		if (this.cursors != null) {
			this.cursors.close();
		}
	}

	public void put(EUID nid, IterativeCursor cursor) {
		Transaction transaction = dbEnv.get().getEnvironment().beginTransaction(null, null);
		try {
			DatabaseEntry key = new DatabaseEntry(nid.toByteArray());
			byte[] valueBytes = serialization.get().toDson(cursor, DsonOutput.Output.PERSIST);
			DatabaseEntry value = new DatabaseEntry(valueBytes);

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

	public Optional<IterativeCursor> get(EUID nid) {
		try {
			DatabaseEntry key = new DatabaseEntry(nid.toByteArray());
			DatabaseEntry value = new DatabaseEntry();

			OperationStatus status = this.cursors.get(null, key, value, LockMode.DEFAULT);
			if (status == OperationStatus.NOTFOUND) {
				return Optional.empty();
			} else if (status != OperationStatus.SUCCESS) {
				fail("Database returned status " + status + " for put operation");
			} else {
				IterativeCursor cursor = serialization.get().fromDson(value.getData(), IterativeCursor.class);
				return Optional.of(cursor);
			}
		} catch (Exception e) {
			fail("Error while getting cursor for '" + nid + "'", e);
		}

		return Optional.empty();
	}
}
