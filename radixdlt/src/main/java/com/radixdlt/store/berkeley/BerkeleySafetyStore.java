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
import com.google.inject.Singleton;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.safety.PersistentSafetyState;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.Longs;
import com.radixdlt.utils.Pair;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

import java.nio.ByteBuffer;
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
public final class BerkeleySafetyStore implements PersistentSafetyState {
	private static final String SAFETY_STORE_NAME = "safety_store";
	private static final Logger logger = LogManager.getLogger();

	private final DatabaseEnvironment dbEnv;
	private Database safetyStore;

	@Inject
	public BerkeleySafetyStore(DatabaseEnvironment dbEnv) {
		this.dbEnv = Objects.requireNonNull(dbEnv, "dbEnv is required");

		this.open();
	}

	private void fail(String message) {
		logger.error(message);
		throw new BerkeleyStoreException(message);
	}

	private void fail(String message, Exception cause) {
		logger.error(message, cause);
		throw new BerkeleyStoreException(message, cause);
	}

	private void open() {
		DatabaseConfig primaryConfig = new DatabaseConfig();
		primaryConfig.setAllowCreate(true);
		primaryConfig.setTransactional(true);

		try {
			// This SuppressWarnings here is valid, as ownership of the underlying
			// resource is not changed here, the resource is just accessed.
			@SuppressWarnings("resource")
			Environment env = this.dbEnv.getEnvironment();
			this.safetyStore = env.openDatabase(null, SAFETY_STORE_NAME, primaryConfig);
		} catch (Exception e) {
			throw new BerkeleyStoreException("Error while opening database", e);
		}

		if (System.getProperty("db.check_integrity", "1").equals("1")) {
			// TODO implement intergrity check
		}
	}

	public Optional<Pair<Long, SafetyState>> get() {
		try (com.sleepycat.je.Cursor cursor = this.safetyStore.openCursor(null, null)) {
			DatabaseEntry pKey = new DatabaseEntry();
			DatabaseEntry value = new DatabaseEntry();
			OperationStatus status = cursor.getLast(pKey, value, LockMode.DEFAULT);
			if (status == OperationStatus.SUCCESS) {
				long lockedView = Longs.fromByteArray(value.getData());
				long epochFound = ByteBuffer.wrap(pKey.getData()).getLong();
				long view = ByteBuffer.wrap(pKey.getData()).getLong(Long.BYTES);

				return Optional.of(Pair.of(epochFound, new SafetyState(View.of(view), View.of(lockedView))));
			} else {
				return Optional.empty();
			}
		}
	}

	@Override
	public void save(Vote vote, SafetyState safetyState) {
		if (!safetyState.getLastVotedView().equals(vote.getView())) {
			throw new IllegalStateException("SafetyState and vote views don't match.");
		}
		long epoch = vote.getVoteData().getProposed().getLedgerHeader().getEpoch();
		long view = vote.getView().number();
		long lockedView = safetyState.getLockedView().number();
		ByteBuffer keyByteBuffer = ByteBuffer.allocate(Long.BYTES * 2);
		keyByteBuffer.putLong(0, epoch);
		keyByteBuffer.putLong(Long.BYTES, view);
		byte[] keyBytes = keyByteBuffer.array();

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
	}
}
