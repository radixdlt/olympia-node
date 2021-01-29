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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.radixdlt.consensus.bft.PersistentVertexStore;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.identifiers.AID;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.LedgerEntry;
import com.radixdlt.store.LedgerEntryConflict;
import com.radixdlt.store.LedgerEntryIndices;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.LedgerEntryStoreResult;
import com.radixdlt.store.SearchCursor;
import com.radixdlt.store.SerializedVertexStoreState;
import com.radixdlt.store.StoreIndex;
import com.radixdlt.store.StoreIndex.LedgerIndexType;
import com.radixdlt.utils.Longs;
import com.radixdlt.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.h2.mvstore.tx.Transaction;
import org.h2.mvstore.tx.TransactionMap;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_BYTES_READ;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_BYTES_WRITE;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_COMMIT;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_CONTAINS;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_CONTAINS_TX;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_CREATE_TX;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_DELETES;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_ENTRIES;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_GET;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_LAST_COMMITTED;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_LAST_VERTEX;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_SAVE;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_SAVE_TX;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_SEARCH;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_STORE;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_TOTAL;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_COMMIT;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_CONTAINS;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_CONTAINS_TX;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_CREATE_TX;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_ENTRIES;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_GET;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_LAST_COMMITTED;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_LAST_VERTEX;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_SAVE;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_SAVE_TX;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_SEARCH;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_STORE;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_TOTAL;
import static com.radixdlt.store.LedgerEntryIndices.ENTRY_INDEX_PREFIX;
import static com.radixdlt.store.mvstore.CommonCounterType.BYTES_READ;
import static com.radixdlt.store.mvstore.CommonCounterType.BYTES_WRITE;
import static com.radixdlt.store.mvstore.CommonCounterType.ELAPSED;
import static com.radixdlt.store.mvstore.CommonCounterType.TOTAL;
import static java.util.stream.Collectors.joining;

public class MVStoreLedgerEntryStore extends MVStoreBase implements LedgerEntryStore, PersistentVertexStore {
	private static final Logger log = LogManager.getLogger();

	private static final String ATOM_INDICES_DB_NAME = "tempo2.atom_indices";
	private static final String DUPLICATE_INDICES_DB_NAME = "tempo2.duplicated_indices";
	private static final String UNIQUE_INDICES_DB_NAME = "tempo2.unique_indices";
	private static final String PENDING_DB_NAME = "tempo2.pending";
	private static final String ATOMS_DB_NAME = "tempo2.atoms";

	private static final byte PREFIX_COMMITTED = 0b0000_0000;

	private final Map<AID, LedgerEntryIndices> currentIndices = new ConcurrentHashMap<>();

	@Inject
	public MVStoreLedgerEntryStore(
		Serialization serialization,
		DatabaseEnvironment dbEnv,
		SystemCounters systemCounters
	) {
		super(
			serialization,
			dbEnv,
			systemCounters,
			ATOMS_DB_NAME,
			Map.of(
				ELAPSED, ELAPSED_BDB_LEDGER_TOTAL,
				TOTAL, COUNT_BDB_LEDGER_TOTAL,
				BYTES_READ, COUNT_BDB_LEDGER_BYTES_READ,
				BYTES_WRITE, COUNT_BDB_LEDGER_BYTES_WRITE
			)
		);
	}

	@Override
	public void reset() {
		inTransaction(this::doReset);
	}

	private Optional<Boolean> doReset(Transaction tx) {
		openMap(ATOMS_DB_NAME, tx).clear();
		openMap(UNIQUE_INDICES_DB_NAME, tx).clear();
		openMap(DUPLICATE_INDICES_DB_NAME, tx).clear();
		openMap(ATOM_INDICES_DB_NAME, tx).clear();
		openMap(PENDING_DB_NAME, tx).clear();

		return SUCCESS;
	}

	@Override
	public void close() {
	}

	@Override
	public boolean contains(AID aid) {
		var key = StoreIndex.from(ENTRY_INDEX_PREFIX, aid.getBytes());

		return withTimeInTx(
			tx -> Optional.of(openMap(UNIQUE_INDICES_DB_NAME, tx).containsKey(key)),
			ELAPSED_BDB_LEDGER_CONTAINS,
			COUNT_BDB_LEDGER_CONTAINS
		).orElse(false);
	}

	@Override
	public Optional<LedgerEntry> get(AID aid) {
		var key = StoreIndex.from(ENTRY_INDEX_PREFIX, aid.getBytes());
		return withTimeInTx(
			tx -> doGet(tx, key),
			ELAPSED_BDB_LEDGER_GET,
			COUNT_BDB_LEDGER_GET
		);
	}

	private Optional<LedgerEntry> doGet(Transaction tx, byte[] key) {
		var result = Optional.ofNullable(openMap(UNIQUE_INDICES_DB_NAME, tx).get(key));

		return result
			.map(v -> sideEffect(v, () -> addBytesRead(key.length + v.length)))
			.flatMap(pKey -> Optional.ofNullable(openMap(ATOMS_DB_NAME, tx).get(pKey)))
			.flatMap(v -> safeFromDson(v, LedgerEntry.class));
	}

	@Override
	public com.radixdlt.store.Transaction createTransaction() {
		return withTime(
			() -> MVStoreTransaction.wrap(startTransaction()),
			ELAPSED_BDB_LEDGER_CREATE_TX,
			COUNT_BDB_LEDGER_CREATE_TX
		);
	}

	@Override
	public LedgerEntryStoreResult store(
		com.radixdlt.store.Transaction tx,
		LedgerEntry atom,
		Set<StoreIndex> uniqueIndices,
		Set<StoreIndex> duplicateIndices
	) {
		var indices = LedgerEntryIndices.from(atom, uniqueIndices, duplicateIndices);
		var atomBytes = toDson(atom);

		if (tx == null) {
			return withTimeInTx(
				transaction -> Optional.of(doStore(atom.getStateVersion(), atom.getAID(), atomBytes, indices, transaction)),
				ELAPSED_BDB_LEDGER_STORE,
				COUNT_BDB_LEDGER_STORE)
				.orElseGet(LedgerEntryStoreResult::success); //Never used
		}

		return withTime(
			() -> doStore(atom.getStateVersion(), atom.getAID(), atomBytes, indices, unwrap(tx)),
			ELAPSED_BDB_LEDGER_STORE,
			COUNT_BDB_LEDGER_STORE
		);
	}

	private LedgerEntryStoreResult doStore(
		long logicalClock,
		AID aid,
		byte[] pData,
		LedgerEntryIndices indices,
		Transaction tx
	) {
		try {
			var primaryKey = toPKey(PREFIX_COMMITTED, logicalClock, aid);
			// put indices in temporary map for key creator to pick up
			currentIndices.put(aid, indices);

			if (openMap(ATOMS_DB_NAME, tx).putIfAbsent(primaryKey, pData) == null) {
				addBytesWrite(pData.length + primaryKey.length);
			} else {
				fail("Atom write for '" + aid + "' failed");
			}

			var uniqueMap = openMap(UNIQUE_INDICES_DB_NAME, tx);
			indices.getUniqueIndices().forEach(ndx -> uniqueMap.put(ndx.asKey(), primaryKey));

			var duplicateMap = openMap(DUPLICATE_INDICES_DB_NAME, tx);
			var mergedPrimaryKey = KeyList.of(primaryKey).toBytes();

			indices.getDuplicateIndices().forEach(ndx -> duplicateMap.merge(ndx.asKey(), mergedPrimaryKey, KeyList::merge));

			var indicesData = toDson(indices);
			if (openMap(ATOM_INDICES_DB_NAME, tx).putIfAbsent(primaryKey, indicesData) == null) {
				addBytesWrite(indicesData.length + primaryKey.length);
			} else {
				log.error("Unique indices of ledgerEntry '" + aid + "' are in conflict, aborting transaction");
				tx.rollback();

				var conflictingAtoms = doGetConflictingAtoms(indices.getUniqueIndices(), tx);

				var ledgerEntry = safeFromDson(pData, LedgerEntry.class)
					.orElseThrow(() -> new MVStoreException("Error while collecting conflicting atoms"));

				return LedgerEntryStoreResult.conflict(new LedgerEntryConflict(ledgerEntry, conflictingAtoms));
			}
		} finally {
			this.currentIndices.remove(aid);
		}
		return LedgerEntryStoreResult.success();
	}

	private ImmutableMap<StoreIndex, LedgerEntry> doGetConflictingAtoms(Set<StoreIndex> uniqueIndices, Transaction tx) {
		var conflictingAtoms = ImmutableMap.<StoreIndex, LedgerEntry>builder();
		try {
			var uniqueIndicesMap = openMap(UNIQUE_INDICES_DB_NAME, tx);

			for (StoreIndex uniqueIndex : uniqueIndices) {
				var key = uniqueIndex.asKey();
				var value = uniqueIndicesMap.get(key);

				if (value != null) {
					addBytesRead(value.length + key.length);
					conflictingAtoms.put(uniqueIndex, fromDson(value, LedgerEntry.class));
				}
			}
		} catch (Exception e) {
			var references = uniqueIndices.stream().map(StoreIndex::toHexString).collect(joining(", "));
			fail(e, "Failed getting conflicting atom for unique indices %s: '%s'", references, e.toString());
		}

		return conflictingAtoms.build();
	}

	@Override
	public void commit(AID aid) {
		withTimeInTx(
			tx -> doCommit(tx, aid),
			ELAPSED_BDB_LEDGER_COMMIT,
			COUNT_BDB_LEDGER_COMMIT
		);
	}

	private Optional<Boolean> doCommit(Transaction tx, AID aid) {
		try {
			var atoms = openMap(ATOMS_DB_NAME, tx);
			var pair = doGetIndices(tx, aid);
			var indices = pair.getFirst();
			var pKey = pair.getSecond();
			var value = atoms.get(pKey);

			if (value == null) {
				fail("Getting pending atom '" + aid + "' failed");
			}

			if (!doDelete(aid, tx, pKey, indices)) {
				fail("Delete of pending atom '" + aid + "' failed");
			}

			return doStore(lcFromPKey(pKey), aid, value, indices, tx).isSuccess() ? SUCCESS : FAILURE;
		} catch (Exception e) {
			fail("Commit of pending atom '" + aid + "' failed", e);
			return FAILURE;
		}
	}

	public Optional<SerializedVertexStoreState> loadLastVertexStoreState() {
		return withTimeInTx(this::doGetLastStoreState, ELAPSED_BDB_LEDGER_LAST_VERTEX, COUNT_BDB_LEDGER_LAST_VERTEX);
	}

	private Optional<SerializedVertexStoreState> doGetLastStoreState(Transaction tx) {
		var pendingStore = openMap(PENDING_DB_NAME, tx);

		return Optional.ofNullable(pendingStore.lastKey()).map(key -> Pair.of(key, pendingStore.get(key)))
			.map(kv -> sideEffect(kv.getSecond(), () -> addBytesRead(kv.getFirst().length + kv.getSecond().length)))
			.flatMap(value -> safeFromDson(value, SerializedVertexStoreState.class));
	}

	@Override
	public void save(com.radixdlt.store.Transaction transaction, VerifiedVertexStoreState vertexStoreState) {
		var tx = (Transaction) transaction.unwrap();
		withTime(() -> doSave(tx, vertexStoreState), ELAPSED_BDB_LEDGER_SAVE_TX, COUNT_BDB_LEDGER_SAVE_TX);
	}

	@Override
	public void save(VerifiedVertexStoreState vertexStoreState) {
		withTimeInTx(tx -> doSave(tx, vertexStoreState), ELAPSED_BDB_LEDGER_SAVE, COUNT_BDB_LEDGER_SAVE);
	}

	private Optional<Boolean> doSave(Transaction tx, VerifiedVertexStoreState vertexStoreState) {
		var pendingStore = openMap(PENDING_DB_NAME, tx);
		var lastKey = pendingStore.lastKey();

		if (lastKey != null) {
			var value = pendingStore.remove(lastKey);
			addBytesRead(lastKey.length + ((value == null) ? 0 : value.length));
		}

		var vertexKey = vertexStoreState.getRoot().getId().asBytes();
		var serializedVertexWithQC = vertexStoreState.toSerialized();
		var vertexEntry = toDson(serializedVertexWithQC, DsonOutput.Output.ALL);
		var status = pendingStore.putIfAbsent(vertexKey, vertexEntry);

		if (status != null) {
			fail("Store of root vertex failed");
		} else {
			addBytesWrite(vertexEntry.length + vertexKey.length);
		}
		return SUCCESS;
	}

	private boolean doDelete(
		AID aid,
		Transaction transaction,
		byte[] pKey,
		LedgerEntryIndices indices
	) {
		try {
			openMap(ATOM_INDICES_DB_NAME, transaction).remove(pKey);
			increment(COUNT_BDB_LEDGER_DELETES);
			currentIndices.put(aid, indices);
			openMap(ATOMS_DB_NAME, transaction).remove(pKey);
			return true;
		} catch (Exception e) {
			return false;
		} finally {
			currentIndices.remove(aid);
		}
	}

	private Pair<LedgerEntryIndices, byte[]> doGetIndices(Transaction tx, AID aid) throws DeserializeException {
		var key = StoreIndex.from(ENTRY_INDEX_PREFIX, aid.getBytes());
		var value = openMap(UNIQUE_INDICES_DB_NAME, tx).get(key);
		if (value == null) {
			fail("Getting primary key of atom '" + aid + "' failed");
		}

		var indices = openMap(ATOM_INDICES_DB_NAME, tx).get(value);
		if (indices == null) {
			fail("Getting indices of atom '" + aid + "' failed");
		}

		return Pair.of(fromDson(indices, LedgerEntryIndices.class), value);
	}

	@Override
	public ImmutableList<LedgerEntry> getNextCommittedLedgerEntries(long stateVersion, int limit) {
		return withTimeInTx(
			tx -> doGetNextCommitted(tx, stateVersion, limit),
			ELAPSED_BDB_LEDGER_ENTRIES,
			COUNT_BDB_LEDGER_ENTRIES
		).orElse(ImmutableList.of());
	}

	private Optional<ImmutableList<LedgerEntry>> doGetNextCommitted(Transaction tx, long stateVersion, int limit) {
		var atomStore = openMap(ATOMS_DB_NAME, tx);
		var uniqueStore = openMap(UNIQUE_INDICES_DB_NAME, tx);
		var ledgerEntries = ImmutableList.<LedgerEntry>builder();
		long proofVersion = -1;
		var fistKey = atomStore.ceilingKey(toPKey(PREFIX_COMMITTED, stateVersion + 1));
		int size = 0;

		var entryIterator = atomStore.entryIterator(fistKey, null);

		while (entryIterator.hasNext() && size <= limit) {
			var entry = entryIterator.next();

			if (entry.getKey()[0] != PREFIX_COMMITTED) {
				// if we've gone beyond committed keys, abort, as this is only for committed atoms
				break;
			}

			var atomId = getAidFromPKey(entry.getKey());
			var pKey = StoreIndex.from(ENTRY_INDEX_PREFIX, atomId.getBytes());
			var value = uniqueStore.get(pKey);

			if (value != null) {
				addBytesRead(value.length + pKey.length);
				LedgerEntry ledgerEntry = safeFromDson(value, LedgerEntry.class)
					.orElseThrow(() -> new MVStoreException("Unable to deserialize ledger entry"));

				if (proofVersion == -1) {
					proofVersion = ledgerEntry.getProofVersion();
				} else if (ledgerEntry.getProofVersion() != proofVersion) {
					break;
				}

				ledgerEntries.add(ledgerEntry);
				++size;
			} else {
				log.error("Unable to fetch ledger entry for Atom ID " + atomId);
			}

			//TODO: is this can happen?
			if (size > limit) {
				throw new IllegalStateException("Unexpected: next committed limit reached");
			}
		}
		return Optional.of(ledgerEntries.build());
	}

	@Override
	public SearchCursor search(LedgerIndexType type, StoreIndex index) {
		return withTime(() -> doSearch(type, index), ELAPSED_BDB_LEDGER_SEARCH, COUNT_BDB_LEDGER_SEARCH);
	}

	private SearchCursor doSearch(LedgerIndexType type, StoreIndex index) {
		byte[] key = index.asKey();

		switch (type) {
			case UNIQUE:
				var uniqueMap = openMap(UNIQUE_INDICES_DB_NAME);
				return uniqueMap.containsKey(key)
					   ? new UniqueSearchCursor(uniqueMap, key)
					   : null;

			case DUPLICATE:
				var duplicateMap = openMap(DUPLICATE_INDICES_DB_NAME);
				return duplicateMap.containsKey(key)
					   ? new DuplicateSearchCursor(duplicateMap, key, false)
					   : null;

			default:
				throw new IllegalStateException("Cursor type " + type + " not supported");
		}
	}

	@Override
	public boolean contains(com.radixdlt.store.Transaction tx, LedgerIndexType type, StoreIndex index) {
		if (tx == null) {
			return withTimeInTx(
				ltx -> Optional.of(doContains(ltx, type, index)),
				ELAPSED_BDB_LEDGER_CONTAINS_TX,
				COUNT_BDB_LEDGER_CONTAINS_TX
			).orElse(false); //Unreachable. Suppressing ".get() without .isPresent()" complain
		}
		return withTime(() -> doContains(unwrap(tx), type, index), ELAPSED_BDB_LEDGER_CONTAINS_TX, COUNT_BDB_LEDGER_CONTAINS_TX);
	}

	private boolean doContains(Transaction tx, LedgerIndexType type, StoreIndex index) {
		final var start = System.nanoTime();
		Objects.requireNonNull(type, "type is required");
		Objects.requireNonNull(index, "index is required");

		return selectStore(tx, type).containsKey(index.asKey());
	}

	private TransactionMap<byte[], byte[]> selectStore(Transaction tx, LedgerIndexType type) {
		switch (type) {
			case UNIQUE:
				return openMap(UNIQUE_INDICES_DB_NAME, tx);
			case DUPLICATE:
				return openMap(DUPLICATE_INDICES_DB_NAME, tx);
			default:
				throw new IllegalStateException("Cursor type " + type + " not supported");
		}
	}

	@Override
	public Optional<AID> getLastCommitted() {
		return withTimeInTx(this::doGetLastCommitted, ELAPSED_BDB_LEDGER_LAST_COMMITTED, COUNT_BDB_LEDGER_LAST_COMMITTED);
	}

	private Optional<AID> doGetLastCommitted(Transaction tx) {
		return Optional.ofNullable(openMap(ATOMS_DB_NAME, tx).lastKey())
			.map(MVStoreLedgerEntryStore::getAidFromPKey);
	}

	private static AID getAidFromPKey(byte[] pKey) {
		return AID.from(pKey, Long.BYTES + 1); // prefix + LC
	}

	private Transaction unwrap(com.radixdlt.store.Transaction tx) {
		return (Transaction) tx.unwrap();
	}

	private static byte[] toPKey(byte prefix, long logicalClock) {
		byte[] pKey = new byte[1 + Long.BYTES];
		pKey[0] = prefix;
		Longs.copyTo(logicalClock, pKey, 1);
		return pKey;
	}

	private static long lcFromPKey(byte[] pKey) {
		return Longs.fromByteArray(pKey, 1);
	}

	private static byte[] toPKey(byte prefix, long logicalClock, AID aid) {
		byte[] pKey = new byte[1 + Long.BYTES + AID.BYTES];
		pKey[0] = prefix;
		Longs.copyTo(logicalClock, pKey, 1);
		System.arraycopy(aid.getBytes(), 0, pKey, Long.BYTES + 1, AID.BYTES);
		return pKey;
	}

	private void fail(String message) {
		log.error(message);
		throw new MVStoreException(message);
	}

	private void fail(String message, Exception cause) {
		log.error(message, cause);
		throw new MVStoreException(message, cause);
	}

	private void fail(Exception cause, String format, Object... params) {
		fail(String.format(format, params), cause);
	}
}
