/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.store.mvstore;

import com.google.inject.Inject;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Longs;
import org.h2.mvstore.tx.Transaction;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_SAFETY_STATE_BYTES_READ;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_SAFETY_STATE_BYTES_WRITE;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_SAFETY_STATE_TOTAL;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_SAFETY_STATE;
import static com.radixdlt.counters.SystemCounters.CounterType.PERSISTENCE_SAFETY_STORE_SAVES;
import static com.radixdlt.store.mvstore.CommonCounterType.BYTES_READ;
import static com.radixdlt.store.mvstore.CommonCounterType.BYTES_WRITE;
import static com.radixdlt.store.mvstore.CommonCounterType.ELAPSED;
import static com.radixdlt.store.mvstore.CommonCounterType.TOTAL;

public class MVStoreSafetyStateStore extends MVStoreBase implements PersistentSafetyStateStore {
	static final String NAME = "safety_store";

	@Inject
	public MVStoreSafetyStateStore(
		Serialization serialization,
		DatabaseEnvironment dbEnv,
		SystemCounters systemCounters
	) {
		super(
			serialization,
			dbEnv,
			systemCounters,
			NAME,
			Map.of(
				ELAPSED, ELAPSED_BDB_SAFETY_STATE,
				TOTAL, COUNT_BDB_SAFETY_STATE_TOTAL,
				BYTES_READ, COUNT_BDB_SAFETY_STATE_BYTES_READ,
				BYTES_WRITE, COUNT_BDB_SAFETY_STATE_BYTES_WRITE
			)
		);
	}

	@Override
	public Optional<SafetyState> getLastState() {
		return withTimeInTx(this::doFindLast);
	}

	private Optional<SafetyState> doFindLast(Transaction tx) {
		var map = openMap(tx);
		var keyLen = new AtomicInteger();
		var dataLen = new AtomicInteger();

		return Optional.ofNullable(map.lastKey())
			.map(v -> sideEffect(v, () -> keyLen.set(v.length)))	//TODO: switch to own Optional and eliminate such hacks
			.map(map::get)
			.map(v -> sideEffect(v, () -> dataLen.set(v.length)))
			.flatMap(this::safetyStateFromDson)
			.map(v -> sideEffect(v, () -> addBytesRead(keyLen.get() + dataLen.get())));
	}

	private Optional<SafetyState> safetyStateFromDson(byte[] value) {
		try {
			return Optional.of(fromDson(value, SafetyState.class));
		} catch (DeserializeException e) {
			return Optional.empty();
		}
	}

	@Override
	public void commitState(SafetyState safetyState) {
		increment(PERSISTENCE_SAFETY_STORE_SAVES);
		withTimeInTx(tx -> doCommitState(tx, safetyState));
	}

	private Optional<Boolean> doCommitState(Transaction tx, SafetyState safetyState) {
		final byte[] key = keyFor(safetyState);
		final byte[] value = toDson(safetyState);

		openMap(tx).put(key, value);
		addBytesWrite(key.length + value.length);

		return SUCCESS;
	}

	@Override
	public void close() {
		// Do nothing
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
