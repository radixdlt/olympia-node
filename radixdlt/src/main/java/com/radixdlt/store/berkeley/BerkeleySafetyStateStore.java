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
import com.radixdlt.utils.Longs;
import com.radixdlt.utils.Pair;
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

	@Inject
	public BerkeleySafetyStateStore(DatabaseEnvironment dbEnv, SystemCounters systemCounters) {
		this.dbEnv = Objects.requireNonNull(dbEnv, "dbEnv is required");
		this.systemCounters = Objects.requireNonNull(systemCounters);

		this.safetyStore = this.open();

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

	public Optional<Pair<Long, SafetyState>> get() {
		final var start = System.nanoTime();
		try (com.sleepycat.je.Cursor cursor = this.safetyStore.openCursor(null, null)) {
			DatabaseEntry pKey = new DatabaseEntry();
			DatabaseEntry value = new DatabaseEntry();
			OperationStatus status = cursor.getLast(pKey, value, LockMode.DEFAULT);
			if (status == OperationStatus.SUCCESS) {
				long lockedView = Longs.fromByteArray(value.getData());
				long epochFound = Longs.fromByteArray(pKey.getData(), 0);
				long view = Longs.fromByteArray(pKey.getData(), Long.BYTES);

				return Optional.of(Pair.of(epochFound, new SafetyState(View.of(view), View.of(lockedView))));
			} else {
				return Optional.empty();
			}
		} finally {
			addTime(start);
		}
	}

	@Override
	public void commitState(Vote vote, SafetyState safetyState) {
		final var start = System.nanoTime();
		try {
			if (!safetyState.getLastVotedView().equals(vote.getView())) {
				throw new IllegalStateException("SafetyState and vote views don't match.");
			}
			long epoch = vote.getVoteData().getProposed().getLedgerHeader().getEpoch();
			long view = vote.getView().number();
			long lockedView = safetyState.getLockedView().number();

			byte[] keyBytes = new byte[Long.BYTES * 2];
			Longs.copyTo(epoch, keyBytes, 0);
			Longs.copyTo(view, keyBytes, Long.BYTES);
			Transaction transaction = dbEnv.getEnvironment().beginTransaction(null, null);
			try {
				DatabaseEntry key = new DatabaseEntry(keyBytes);
				DatabaseEntry data = new DatabaseEntry(Longs.toByteArray(lockedView));

				OperationStatus status = this.safetyStore.put(transaction, key, data);
				if (status != OperationStatus.SUCCESS) {
					fail("Database returned status " + status + " for put operation");
				}

				transaction.commit();
			} catch (Exception e) {
				transaction.abort();
				fail("Error while storing safety state for " + safetyState, e);
			}
		} finally {
			addTime(start);
		}
	}

	private void addTime(long start) {
		final var elapsed = (System.nanoTime() - start + 500L) / 1000L;
		this.systemCounters.add(CounterType.ELAPSED_BDB_SAFETY_STATE, elapsed);
	}
}
