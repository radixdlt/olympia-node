/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.store.berkeley;

import com.google.inject.Inject;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Longs;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.database.DatabaseEnvironment;

import java.util.Objects;

/**
 * Store which persists state required to preserve the networks safety in case of a
 * node restart.
 *
 * TODO: Prune saved safety state.
 */
public final class BerkeleySafetyStateStore implements PersistentSafetyStateStore {
	private static final String SAFETY_STORE_NAME = "safety_store";
	private static final Logger logger = LogManager.getLogger();

	private final DatabaseEnvironment dbEnv;
	private final Database safetyStore;
	private final SystemCounters systemCounters;
	private Serialization serialization;

	@Inject
	public BerkeleySafetyStateStore(
		DatabaseEnvironment dbEnv,
		Serialization serialization,
		SystemCounters systemCounters
	) {
		this.dbEnv = Objects.requireNonNull(dbEnv, "dbEnv is required");
		this.serialization = Objects.requireNonNull(serialization);

		this.safetyStore = this.open();
			this.systemCounters = Objects.requireNonNull(systemCounters);

		if (Boolean.valueOf(System.getProperty("db.check_integrity", "true"))) {
			// TODO implement integrity check
		}
	}

	private void fail(String message) {
		logger.error(message);
		throw new BerkeleyStoreException(message);
	}

	private void fail(String message, Exception cause) {
		logger.error(message, cause);
		throw new BerkeleyStoreException(message, cause);
	}

	private Database open() {
		DatabaseConfig primaryConfig = new DatabaseConfig();
		primaryConfig.setAllowCreate(true);
		primaryConfig.setTransactional(true);

		try {
			// This SuppressWarnings here is valid, as ownership of the underlying
			// resource is not changed here, the resource is just accessed.
			@SuppressWarnings("resource")
			Environment env = this.dbEnv.getEnvironment();
			return env.openDatabase(null, SAFETY_STORE_NAME, primaryConfig);
		} catch (Exception e) {
			throw new BerkeleyStoreException("Error while opening database", e);
		}
	}

	@Override
	public void close() {
		if (this.safetyStore != null) {
			this.safetyStore.close();
		}
	}

	public Optional<SafetyState> get() {
		final var start = System.nanoTime();
		try (com.sleepycat.je.Cursor cursor = this.safetyStore.openCursor(null, null)) {
			DatabaseEntry pKey = new DatabaseEntry();
			DatabaseEntry value = new DatabaseEntry();
			OperationStatus status = cursor.getLast(pKey, value, LockMode.DEFAULT);
			if (status == OperationStatus.SUCCESS) {
				addBytesRead(pKey.getSize() + value.getSize());
				try {
					final SafetyState deserializedState =
						serialization.fromDson(value.getData(), SafetyState.class);
					return Optional.of(deserializedState);
				} catch (DeserializeException ex) {
					logger.error("Failed to deserialize persisted SafetyState", ex);
					return Optional.empty();
				}
			} else {
				return Optional.empty();
			}
		} finally {
			addTime(start);
		}
	}



	@Override
	public void commitState(SafetyState safetyState) {
		this.systemCounters.increment(CounterType.PERSISTENCE_SAFETY_STORE_SAVES);

		final var start = System.nanoTime();

		final Transaction transaction =
			dbEnv.getEnvironment().beginTransaction(null, null);
		try {
			final byte[] serializedState = serialization.toDson(safetyState, DsonOutput.Output.PERSIST);

			final DatabaseEntry key = new DatabaseEntry(keyFor(safetyState));
			final DatabaseEntry data = new DatabaseEntry(serializedState);

			final OperationStatus status = this.safetyStore.put(transaction, key, data);
			if (status != OperationStatus.SUCCESS) {
				fail("Database returned status " + status + " for put operation");
			} else {
				addBytesWrite(key.getSize() + data.getSize());
			}

			transaction.commit();
		} catch (Exception e) {
			transaction.abort();
			fail("Error while storing safety state for " + safetyState, e);
		} finally {
			addTime(start);
		}
	}

	private void addTime(long start) {
		final var elapsed = (System.nanoTime() - start + 500L) / 1000L;
		this.systemCounters.add(CounterType.ELAPSED_BDB_SAFETY_STATE, elapsed);
		this.systemCounters.increment(CounterType.COUNT_BDB_SAFETY_STATE);
	}

	private void addBytesRead(int bytesRead) {
		this.systemCounters.add(CounterType.COUNT_BDB_SAFETY_STATE_BYTES_READ, bytesRead);
	}

	private void addBytesWrite(int bytesWrite) {
		this.systemCounters.add(CounterType.COUNT_BDB_SAFETY_STATE_BYTES_WRITE, bytesWrite);
	}

	private byte[] keyFor(SafetyState safetyState) {
		long epoch = safetyState.getLastVote().map(Vote::getEpoch).orElse(0L);
		long view = safetyState.getLastVote().map(Vote::getView).orElse(View.genesis()).number();

		byte[] keyBytes = new byte[Long.BYTES * 2];
		Longs.copyTo(epoch, keyBytes, 0);
		Longs.copyTo(view, keyBytes, Long.BYTES);

		return keyBytes;
	}
}
