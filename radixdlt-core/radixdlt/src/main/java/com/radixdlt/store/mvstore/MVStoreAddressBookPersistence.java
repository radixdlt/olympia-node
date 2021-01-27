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

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.addressbook.PeerPersistence;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.Serialization;
import org.h2.mvstore.tx.Transaction;

import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_ADDRESS_BOOK_BYTES_READ;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_ADDRESS_BOOK_BYTES_WRITE;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_ADDRESS_BOOK_DELETES;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_ADDRESS_BOOK_TOTAL;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_ADDRESS_BOOK;
import static com.radixdlt.store.mvstore.CommonCounterType.BYTES_READ;
import static com.radixdlt.store.mvstore.CommonCounterType.BYTES_WRITE;
import static com.radixdlt.store.mvstore.CommonCounterType.ELAPSED;
import static com.radixdlt.store.mvstore.CommonCounterType.TOTAL;

public class MVStoreAddressBookPersistence extends MVStoreBase implements PeerPersistence {
	public static final String NAME = "peers_by_nid";

	public MVStoreAddressBookPersistence(
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
				ELAPSED, ELAPSED_BDB_ADDRESS_BOOK,
				TOTAL, COUNT_BDB_ADDRESS_BOOK_TOTAL,
				BYTES_READ, COUNT_BDB_ADDRESS_BOOK_BYTES_READ,
				BYTES_WRITE, COUNT_BDB_ADDRESS_BOOK_BYTES_WRITE
			)
		);
	}

	public void start() {
	}

	public void reset() {
		inTransaction(this::doClear);
	}

	private Optional<Boolean> doClear(Transaction tx) {
		openMap(tx).clear();
		return SUCCESS;
	}

	@Override
	public boolean savePeer(PeerWithSystem peer) {
		return withTimeInTx(tx -> doSavePeer(tx, peer)).orElse(false);
	}

	private Optional<Boolean> doSavePeer(Transaction tx, PeerWithSystem peer) {
		var key = peer.getNID().toByteArray();
		var value = toDson(peer);

		openMap(tx).put(key, value);
		addBytesWrite(key.length + value.length);

		return SUCCESS;
	}

	@Override
	public boolean deletePeer(EUID nid) {
		return withTimeInTx(tx -> doDeletePeer(tx, nid)).orElse(false);
	}

	private Optional<Boolean> doDeletePeer(Transaction tx, EUID nid) {
		boolean result = openMap(tx).remove(nid.toByteArray()) != null;
		increment(COUNT_BDB_ADDRESS_BOOK_DELETES);
		return result ? SUCCESS : FAILURE;
	}

	@Override
	public void forEachPersistedPeer(Consumer<PeerWithSystem> consumer) {
		inTransaction(tx -> doApply(tx, consumer));
	}

	private Optional<Boolean> doApply(Transaction tx, Consumer<PeerWithSystem> consumer) {
		openMap(tx).forEach((key, value) -> applySingle(value, consumer));
		return SUCCESS;
	}

	private void applySingle(byte[] value, Consumer<PeerWithSystem> consumer) {
		try {
			consumer.accept(fromDson(value, PeerWithSystem.class));
		} catch (DeserializeException ex) {
			throw new UncheckedIOException("Error while loading database", ex);
		}
	}

	@Override
	public void close() {
		//Do nothing
	}
}
