package com.radixdlt.tempo.store.berkeley;

import com.google.inject.Singleton;
import com.radixdlt.common.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.store.SampleStore;
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
import org.radix.time.TemporalProof;

import javax.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@Singleton
public class BerkeleySampleStore implements SampleStore {
	private static final Logger logger = Logging.getLogger("store.samples");
	private static final String COLLECTED_SAMPLES_DB_NAME = "tempo2.samples.collected";
	private static final String LOCAL_SAMPLES_DB_NAME = "tempo2.samples.local";

	private final DatabaseEnvironment dbEnv;
	private final Serialization serialization;
	// TODO use prefix instead of two different databases (use type + aid as primary)
	private Database collectedSamples;
	private Database localSamples;

	@Inject
	public BerkeleySampleStore(DatabaseEnvironment dbEnv, Serialization serialization) {
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
			Environment dbEnv = this.dbEnv.getEnvironment();
			this.collectedSamples = dbEnv.openDatabase(null, COLLECTED_SAMPLES_DB_NAME, primaryConfig);
			this.localSamples = dbEnv.openDatabase(null, LOCAL_SAMPLES_DB_NAME, primaryConfig);
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
			env.truncateDatabase(transaction, COLLECTED_SAMPLES_DB_NAME, false);
			env.truncateDatabase(transaction, LOCAL_SAMPLES_DB_NAME, false);
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
		if (this.collectedSamples != null) {
			this.collectedSamples.close();
		}
		if (this.localSamples != null) {
			this.localSamples.close();
		}
	}

	private Optional<TemporalProof> getTemporalProof(AID aid, Database database) {
		try {
			DatabaseEntry key = new DatabaseEntry(aid.getBytes());
			DatabaseEntry value = new DatabaseEntry();

			OperationStatus status = database.get(null, key, value, LockMode.DEFAULT);
			if (status == OperationStatus.NOTFOUND) {
				return Optional.empty();
			} else if (status != OperationStatus.SUCCESS) {
				fail("Database returned status " + status + " for get operation");
			} else {
				TemporalProof samples = serialization.fromDson(value.getData(), TemporalProof.class);
				return Optional.of(samples);
			}
		} catch (Exception e) {
			fail("Error while getting samples for '" + aid + "'", e);
		}

		return Optional.empty();
	}

	private void add(TemporalProof temporalProof, Database database) {
		Transaction transaction = dbEnv.getEnvironment().beginTransaction(null, null);
		AID aid = temporalProof.getAID();
		try {
			DatabaseEntry key = new DatabaseEntry(aid.getBytes());
			DatabaseEntry value = new DatabaseEntry();

			TemporalProof aggregatedProof = null;
			OperationStatus status = database.get(null, key, value, LockMode.DEFAULT);
			if (status == OperationStatus.NOTFOUND) {
				aggregatedProof = temporalProof;
			} else if (status != OperationStatus.SUCCESS) {
				fail("Database returned status " + status + " for get operation");
			} else {
				TemporalProof existing = serialization.fromDson(value.getData(), TemporalProof.class);
				temporalProof.merge(existing);
				aggregatedProof = temporalProof;
			}

			value.setData(serialization.toDson(aggregatedProof, DsonOutput.Output.PERSIST));
			status = database.put(transaction, key, value);
			if (status != OperationStatus.SUCCESS) {
				fail("Database returned status " + status + " for put operation");
			}

			transaction.commit();
		} catch (Exception e) {
			transaction.abort();
			fail("Error while adding samples for '" + aid + "'", e);
		}
	}

	/**
	 * Gets the aggregated temporal proofs associated with a certain {@link AID}.
	 * @param aid The {@link AID}.
	 * @return The temporal proof associated with the given {@link AID} (if any)
	 */
	@Override
	public Optional<TemporalProof> getCollected(AID aid) {
		return getTemporalProof(aid, this.collectedSamples);
	}

	/**
	 * Gets the local temporal proof branch of this node of a certain {@link AID}.
	 * @param aid The {@link AID}.
	 * @return The temporal proof associated with the given {@link AID} (if any)
	 */
	@Override
	public Optional<TemporalProof> getLocal(AID aid) {
		return getTemporalProof(aid, this.localSamples);
	}

	/**
	 * Appends a temporal proof to this store.
	 * If a temporal proof with the same {@link AID} is already stored, the proofs will be merged.
	 *
	 * @param temporalProof The temporal proof
	 */
	@Override
	public void addLocal(TemporalProof temporalProof) {
		add(temporalProof, this.localSamples);
	}

	/**
	 * Appends a temporal proof to this store.
	 * If a temporal proof with the same {@link AID} is already stored, the proofs will be merged.
	 *
	 * @param temporalProof The temporal proof
	 */
	@Override
	public void addCollected(TemporalProof temporalProof) {
		add(temporalProof, this.collectedSamples);
	}
}
