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

import com.radixdlt.statecomputer.CommittedAtom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.database.DatabaseEnvironment;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.consensus.bft.PersistentVertexStore;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.identifiers.AID;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.LedgerEntryConflict;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.LedgerEntryStoreResult;
import com.radixdlt.store.LedgerSearchMode;
import com.radixdlt.store.NextCommittedLimitReachedException;
import com.radixdlt.store.SearchCursor;
import com.radixdlt.store.StoreIndex;
import com.radixdlt.store.StoreIndex.LedgerIndexType;
import com.radixdlt.store.Transaction;
import com.radixdlt.store.berkeley.atom.AppendLog;
import com.radixdlt.store.berkeley.atom.SimpleAppendLog;
import com.radixdlt.utils.Longs;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.UniqueConstraintException;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_CONTAINS;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_CONTAINS_TX;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_CREATE_TX;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_ENTRIES;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_GET;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_GET_FIRST;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_GET_LAST;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_GET_NEXT;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_GET_PREV;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_LAST_COMMITTED;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_LAST_VERTEX;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_SAVE;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_SAVE_TX;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_SEARCH;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_BDB_LEDGER_STORE;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_CONTAINS;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_CONTAINS_TX;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_CREATE_TX;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_ENTRIES;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_GET;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_GET_FIRST;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_GET_LAST;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_GET_NEXT;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_GET_PREV;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_LAST_COMMITTED;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_LAST_VERTEX;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_SAVE;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_SAVE_TX;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_SEARCH;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_BDB_LEDGER_STORE;
import static com.radixdlt.store.LedgerEntryStoreResult.conflict;
import static com.radixdlt.store.LedgerEntryStoreResult.ioFailure;
import static com.radixdlt.store.LedgerEntryStoreResult.success;
import static com.radixdlt.store.LedgerSearchMode.EXACT;
import static com.radixdlt.store.LedgerSearchMode.RANGE;
import static com.radixdlt.store.StoreIndex.from;
import static com.radixdlt.store.berkeley.AtomSecondaryCreator.creator;
import static com.radixdlt.store.berkeley.BerkeleyTransaction.wrap;
import static com.radixdlt.store.berkeley.LedgerEntryIndices.ENTRY_INDEX_PREFIX;
import static com.radixdlt.store.berkeley.LedgerEntryIndices.makeIndices;
import static com.radixdlt.utils.Longs.fromByteArray;
import static com.sleepycat.je.LockMode.DEFAULT;
import static com.sleepycat.je.OperationStatus.SUCCESS;

import static java.lang.String.format;

@Singleton
public final class BerkeleyLedgerEntryStore implements LedgerEntryStore, PersistentVertexStore {
	private static final Logger log = LogManager.getLogger();

	private static final String DUPLICATE_INDICES_DB_NAME = "tempo2.duplicated_indices";
	private static final String UNIQUE_INDICES_DB_NAME = "tempo2.unique_indices";
	private static final String PENDING_DB_NAME = "tempo2.pending";
	private static final String ATOMS_DB_NAME = "tempo2.atoms";
	private static final String ATOM_LOG = "radix.ledger";

	private final Serialization serialization;
	private final DatabaseEnvironment dbEnv;
	private final SystemCounters systemCounters;

	private final Map<AID, LedgerEntryIndices> currentIndices = new ConcurrentHashMap<>();

	private Database atomsDatabase; // Atoms by primary keys (state version + AID bytes, no prefixes)
	private Database particlesDatabase; // Atoms by primary keys (state version + AID bytes, no prefixes)
	private Database vertexStoreDatabase; // Vertex Store
	private SecondaryDatabase uniqueIndicesDatabase; // Atoms by secondary unique indices (with prefixes)
	private SecondaryDatabase duplicatedIndicesDatabase; // Atoms by secondary duplicate indices (with prefixes)
	private AppendLog atomLog; //Atom data append only log

	@Inject
	public BerkeleyLedgerEntryStore(
		Serialization serialization,
		DatabaseEnvironment dbEnv,
		SystemCounters systemCounters
	) {
		this.serialization = Objects.requireNonNull(serialization);
		this.dbEnv = Objects.requireNonNull(dbEnv);
		this.systemCounters = Objects.requireNonNull(systemCounters);

		this.open();
	}

	@Override
	public void reset() {
		dbEnv.withLock(() -> {
			com.sleepycat.je.Transaction transaction = null;
			try {
				// This SuppressWarnings here is valid, as ownership of the underlying
				// resource is not changed here, the resource is just accessed.
				@SuppressWarnings("resource")
				var env = dbEnv.getEnvironment();
				transaction = env.beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
				env.truncateDatabase(transaction, ATOMS_DB_NAME, false);
				env.truncateDatabase(transaction, UNIQUE_INDICES_DB_NAME, false);
				env.truncateDatabase(transaction, DUPLICATE_INDICES_DB_NAME, false);
				env.truncateDatabase(transaction, PENDING_DB_NAME, false);
				transaction.commit();
			} catch (DatabaseNotFoundException e) {
				if (transaction != null) {
					transaction.abort();
				}

				log.warn("Error while resetting database, database not found", e);
			} catch (Exception e) {
				if (transaction != null) {
					transaction.abort();
				}

				throw new BerkeleyStoreException("Error while resetting databases", e);
			}
		});
	}

	@Override
	public void close() {
		safeClose(uniqueIndicesDatabase);
		safeClose(duplicatedIndicesDatabase);
		safeClose(atomsDatabase);
		safeClose(vertexStoreDatabase);

		if (atomLog != null) {
			atomLog.close();
		}
	}

	@Override
	public boolean contains(AID aid) {
		return withTime(() -> {
			var key = entry(from(ENTRY_INDEX_PREFIX, aid.getBytes()));
			return SUCCESS == uniqueIndicesDatabase.get(null, key, null, DEFAULT);
		}, ELAPSED_BDB_LEDGER_CONTAINS, COUNT_BDB_LEDGER_CONTAINS);
	}

	@Override
	public Optional<CommittedAtom> get(AID aid) {
		return withTime(() -> {
			try {
				var key = entry(from(ENTRY_INDEX_PREFIX, aid.getBytes()));
				var value = entry();

				if (uniqueIndicesDatabase.get(null, key, value, DEFAULT) == SUCCESS) {
					value.setData(atomLog.read(fromByteArray(value.getData())));

					addBytesRead(value, key);
					return Optional.of(restoreCommittedAtom(value.getData()));
				}
			} catch (Exception e) {
				fail("Get of atom '" + aid + "' failed", e);
			}

			return Optional.empty();
		}, ELAPSED_BDB_LEDGER_GET, COUNT_BDB_LEDGER_GET);
	}

	@Override
	public Transaction createTransaction() {
		return withTime(
			() -> wrap(beginTransaction()),
			ELAPSED_BDB_LEDGER_CREATE_TX,
			COUNT_BDB_LEDGER_CREATE_TX
		);
	}

	@Override
	public LedgerEntryStoreResult store(
		Transaction tx,
		CommittedAtom atom,
		Set<StoreIndex> uniqueIndices,
		Set<StoreIndex> duplicateIndices
	) {
		return withTime(() -> {
			try {
				return doStore(atom, makeIndices(atom, uniqueIndices, duplicateIndices), unwrap(tx));
			} catch (Exception e) {
				throw new BerkeleyStoreException("Commit of atom failed", e);
			}
		}, ELAPSED_BDB_LEDGER_STORE, COUNT_BDB_LEDGER_STORE);
	}

	@Override
	public Optional<SerializedVertexStoreState> loadLastVertexStoreState() {
		return withTime(() -> {
			try (var cursor = vertexStoreDatabase.openCursor(null, null)) {
				var pKey = entry();
				var value = entry();
				var status = cursor.getLast(pKey, value, DEFAULT);

				if (status == SUCCESS) {
					addBytesRead(value, pKey);
					try {
						return Optional.of(serialization.fromDson(value.getData(), SerializedVertexStoreState.class));
					} catch (DeserializeException e) {
						throw new IllegalStateException(e);
					}
				} else {
					return Optional.empty();
				}
			}
		}, ELAPSED_BDB_LEDGER_LAST_VERTEX, COUNT_BDB_LEDGER_LAST_VERTEX);
	}

	@Override
	public void save(Transaction transaction, VerifiedVertexStoreState vertexStoreState) {
		withTime(
			() -> doSave(transaction.unwrap(), vertexStoreState),
			ELAPSED_BDB_LEDGER_SAVE_TX,
			COUNT_BDB_LEDGER_SAVE_TX
		);
	}

	@Override
	public void save(VerifiedVertexStoreState vertexStoreState) {
		withTime(() -> {
			var transaction = beginTransaction();
			doSave(transaction, vertexStoreState);
			transaction.commit();
		}, ELAPSED_BDB_LEDGER_SAVE, COUNT_BDB_LEDGER_SAVE);
	}

	private void open() {
		var primaryConfig = buildPrimaryConfig();
		var uniqueIndicesConfig = buildUniqueIndicesConfig();
		var duplicateIndicesConfig = buildDuplicatesConfig();
		var pendingConfig = buildPendingConfig();

		try {
			// This SuppressWarnings here is valid, as ownership of the underlying
			// resource is not changed here, the resource is just accessed.
			@SuppressWarnings("resource")
			var env = dbEnv.getEnvironment();
			atomsDatabase = env.openDatabase(null, ATOMS_DB_NAME, primaryConfig);
			uniqueIndicesDatabase = env.openSecondaryDatabase(null, UNIQUE_INDICES_DB_NAME, atomsDatabase, uniqueIndicesConfig);
			duplicatedIndicesDatabase = env.openSecondaryDatabase(null, DUPLICATE_INDICES_DB_NAME, atomsDatabase, duplicateIndicesConfig);
			vertexStoreDatabase = env.openDatabase(null, PENDING_DB_NAME, pendingConfig);

			atomLog = SimpleAppendLog.openCompressed(new File(env.getHome(), ATOM_LOG).getAbsolutePath(), systemCounters);
		} catch (Exception e) {
			throw new BerkeleyStoreException("Error while opening databases", e);
		}

		if (System.getProperty("db.check_integrity", "1").equals("1")) {
			// TODO implement integrity check
			// TODO perhaps we should implement recovery instead?
		}
	}

	private DatabaseConfig buildPendingConfig() {
		return new DatabaseConfig()
			.setBtreeComparator(lexicographicalComparator())
			.setAllowCreate(true)
			.setTransactional(true);
	}

	private SecondaryConfig buildDuplicatesConfig() {
		return (SecondaryConfig) new SecondaryConfig()
			.setMultiKeyCreator(creator(currentIndices, LedgerEntryIndices::getDuplicateIndices))
			.setAllowCreate(true)
			.setTransactional(true)
			.setSortedDuplicates(true);
	}

	private SecondaryConfig buildUniqueIndicesConfig() {
		return (SecondaryConfig) new SecondaryConfig()
			.setMultiKeyCreator(creator(currentIndices, LedgerEntryIndices::getUniqueIndices))
			.setAllowCreate(true)
			.setTransactional(true);
	}

	private DatabaseConfig buildPrimaryConfig() {
		return new DatabaseConfig()
			.setAllowCreate(true)
			.setTransactional(true)
			.setKeyPrefixing(true)
			.setBtreeComparator(lexicographicalComparator());
	}

	private static void safeClose(Database database) {
		if (database != null) {
			database.close();
		}
	}

	private static void fail(String message) {
		log.error(message);
		throw new BerkeleyStoreException(message);
	}

	private static void fail(String message, Exception cause) {
		log.error(message, cause);
		throw new BerkeleyStoreException(message, cause);
	}

	private void withTime(Runnable runnable, CounterType elapsed, CounterType count) {
		withTime(
			() -> {
				runnable.run();
				return null;
			},
			elapsed,
			count
		);
	}

	private <T> T withTime(Supplier<T> supplier, CounterType elapsed, CounterType count) {
		final var start = System.nanoTime();
		try {
			return supplier.get();
		} finally {
			addTime(start, elapsed, count);
		}
	}

	private void doSave(com.sleepycat.je.Transaction transaction, VerifiedVertexStoreState vertexStoreState) {
		removeLastPending(transaction);

		var rootId = vertexStoreState.getRoot().getId();
		var vertexKey = entry(rootId.asBytes());
		var vertexEntry = serializeAll(vertexStoreState.toSerialized());

		failIfNotSuccess(
			vertexStoreDatabase.put(transaction, vertexKey, vertexEntry),
			"Store of root vertex with ID",
			rootId
		);

		addBytesWrite(vertexEntry, vertexKey);
	}

	private void removeLastPending(com.sleepycat.je.Transaction transaction) {
		try (var cursor = vertexStoreDatabase.openCursor(transaction, null)) {
			var pKey = entry();
			var value = entry();
			var status = cursor.getLast(pKey, value, DEFAULT);

			if (status == SUCCESS) {
				addBytesRead(value, pKey);
				vertexStoreDatabase.delete(transaction, pKey);
			}
		} catch (Exception e) {
			transaction.abort();
			fail("Commit of atom failed", e);
		}
	}

	private LedgerEntryStoreResult doStore(
		CommittedAtom atom,
		LedgerEntryIndices indices,
		com.sleepycat.je.Transaction transaction
	) throws DeserializeException {
		var aid = atom.getAID();
		var atomData = serialize(atom);

		try {
			//Write atom data as soon as possible
			var offset = atomLog.write(atomData.getData());

			// put indices in temporary map for key creator to pick up
			currentIndices.put(aid, indices);

			var pKey = toPKey(atom.getStateVersion(), aid);
			var atomPosData = entry(Longs.toByteArray(offset));

			failIfNotSuccess(atomsDatabase.putNoOverwrite(transaction, pKey, atomPosData), "Atom write for", aid);



			addBytesWrite(atomData, pKey);
		} catch (IOException e) {
			return ioFailure(e);
		} catch (UniqueConstraintException e) {
			log.error("Unique indices of ledgerEntry '" + aid + "' are in conflict, aborting transaction");
			transaction.abort();

			return conflict(collectConflictingData(atomData.getData(), indices));
		} finally {
			currentIndices.remove(aid);
		}
		return success();
	}

	private com.sleepycat.je.Transaction beginTransaction() {
		return dbEnv.getEnvironment().beginTransaction(null, null);
	}

	private <T> DatabaseEntry serialize(T instance) {
		return entry(serialization.toDson(instance, Output.PERSIST));
	}

	private <T> DatabaseEntry serializeAll(T instance) {
		return entry(serialization.toDson(instance, Output.ALL));
	}

	private LedgerEntryConflict collectConflictingData(
		byte[] ledgerEntryData, LedgerEntryIndices indices
	) throws DeserializeException {
		return new LedgerEntryConflict(
			restoreCommittedAtom(ledgerEntryData),
			doGetConflictingAtoms(indices.getUniqueIndices(), null)
		);
	}

	private ImmutableMap<StoreIndex, CommittedAtom> doGetConflictingAtoms(
		Set<StoreIndex> indices,
		com.sleepycat.je.Transaction transaction
	) {
		var conflictingAtoms = ImmutableMap.<StoreIndex, CommittedAtom>builder();
		try {
			var key = entry();
			var value = entry();

			for (var uniqueIndex : indices) {
				key.setData(uniqueIndex.asKey());
				if (uniqueIndicesDatabase.get(transaction, key, value, DEFAULT) == SUCCESS) {
					addBytesRead(value, key);
					conflictingAtoms.put(uniqueIndex, restoreCommittedAtom(value.getData()));
				}
			}
		} catch (Exception e) {
			var indicesText = indices.stream().map(StoreIndex::toHexString).collect(Collectors.joining(", "));
			fail(format("Failed getting conflicting atom for unique indices %s: '%s'", indicesText, e.toString()), e);
		}

		return conflictingAtoms.build();
	}

	@Override
	public ImmutableList<CommittedAtom> getNextCommittedAtoms(long stateVersion, int limit) throws NextCommittedLimitReachedException {
		final var start = System.nanoTime();
		// when querying committed atoms, no need to worry about transaction as they aren't going away
		try (
			var atomCursor = atomsDatabase.openCursor(null, null);
			var uqCursor = uniqueIndicesDatabase.openCursor(null, null)
		) {

			final var atoms = Lists.<CommittedAtom>newArrayList();
			final var atomSearchKey = toPKey(stateVersion + 1);

			var atomCursorStatus = atomCursor.getSearchKeyRange(atomSearchKey, null, DEFAULT);

			while (atomCursorStatus == SUCCESS && atoms.size() <= limit) {
				var atomId = getAidFromPKey(atomSearchKey);
				try {
					var key = entry(from(ENTRY_INDEX_PREFIX, atomId.getBytes()));
					var value = entry();
					var uqCursorStatus = uqCursor.getSearchKey(key, value, DEFAULT);

					// TODO when uqCursor fails to fetch value, which means some form of DB corruption has occurred,
					//  how should we handle it?
					if (uqCursorStatus == SUCCESS) {
						var offset = fromByteArray(value.getData());
						atoms.add(restoreCommittedAtom(atomLog.read(offset)));
					}
				} catch (Exception e) {
					log.error(format("Unable to fetch ledger entry for Atom ID %s", atomId), e);
				}
				atomCursorStatus = atomCursor.getNext(atomSearchKey, null, DEFAULT);
			}

			if (atoms.size() < limit) {
				// Assume that the last entry is a complete commit
				// if we ran out of entries at or before limit
				return ImmutableList.copyOf(atoms);
			}

			int lastIndex;
			// Otherwise we search backwards for the change in state version
			for (lastIndex = limit - 1; lastIndex >= 0; lastIndex--) {
				if (atoms.get(lastIndex).getHeaderAndProof().isPresent()) {
					break;
				}
			}

			if (lastIndex < 0) {
				throw new NextCommittedLimitReachedException(limit);
			}

			return ImmutableList.copyOf(atoms.subList(0, lastIndex + 1));
		} finally {
			addTime(start, ELAPSED_BDB_LEDGER_ENTRIES, COUNT_BDB_LEDGER_ENTRIES);
		}
	}

	@Override
	public SearchCursor search(LedgerIndexType type, StoreIndex index, LedgerSearchMode mode) {
		Objects.requireNonNull(type, "type is required");
		Objects.requireNonNull(index, "index is required");
		Objects.requireNonNull(mode, "mode is required");

		return withTime(() -> {
			try (var databaseCursor = toSecondaryCursor(type)) {
				var pKey = entry();
				var key = entry(index.asKey());

				if (mode == EXACT) {
					if (databaseCursor.getSearchKey(key, pKey, null, DEFAULT) == SUCCESS) {
						return new BerkeleySearchCursor(this, type, pKey.getData(), key.getData());
					}
				} else if (mode == RANGE) {
					if (databaseCursor.getSearchKeyRange(key, pKey, null, DEFAULT) == SUCCESS) {
						return new BerkeleySearchCursor(this, type, pKey.getData(), key.getData());
					}
				}

				return null;
			}
		}, ELAPSED_BDB_LEDGER_SEARCH, COUNT_BDB_LEDGER_SEARCH);
	}

	@Override
	public boolean contains(Transaction tx, LedgerIndexType type, StoreIndex index, LedgerSearchMode mode) {
		Objects.requireNonNull(type, "type is required");
		Objects.requireNonNull(index, "index is required");
		Objects.requireNonNull(mode, "mode is required");

		return withTime(() -> {
			try (var databaseCursor = toSecondaryCursor(unwrap(tx), type)) {
				var pKey = entry();
				var key = entry(index.asKey());

				switch (mode) {
					case EXACT:
						return databaseCursor.getSearchKey(key, pKey, null, DEFAULT) == SUCCESS;
					case RANGE:
						return databaseCursor.getSearchKeyRange(key, pKey, null, DEFAULT) == SUCCESS;
					default:
						return false;
				}
			}
		}, ELAPSED_BDB_LEDGER_CONTAINS_TX, COUNT_BDB_LEDGER_CONTAINS_TX);
	}

	@Override
	public Optional<AID> getLastCommitted() {
		return withTime(() -> {
			try (var cursor = atomsDatabase.openCursor(null, null)) {
				var pKey = entry();
				var value = entry();

				return Optional.of(cursor.getLast(pKey, value, DEFAULT))
					.filter(status -> status == SUCCESS)
					.map(status -> getAidFromPKey(pKey))
					.map(v -> {
						addBytesRead(value, pKey);
						return v;
					});
			}
		}, ELAPSED_BDB_LEDGER_LAST_COMMITTED, COUNT_BDB_LEDGER_LAST_COMMITTED);
	}

	BerkeleySearchCursor getNext(BerkeleySearchCursor cursor) {
		return withTime(() -> {
			try (var databaseCursor = toSecondaryCursor(cursor.getType())) {
				var pKey = entry(cursor.getPrimary());
				var key = entry(cursor.getIndex());

				return Optional.of(databaseCursor.getSearchBothRange(key, pKey, null, DEFAULT))
					.filter(status -> status == SUCCESS)
					.map(status -> databaseCursor.getNextDup(key, pKey, null, LockMode.DEFAULT))
					.filter(status -> status == SUCCESS)
					.map(status -> new BerkeleySearchCursor(this, cursor.getType(), pKey.getData(), key.getData()))
					.orElse(null);

			} catch (Exception ex) {
				throw new BerkeleyStoreException("Error while advancing cursor", ex);
			}
		}, ELAPSED_BDB_LEDGER_GET_NEXT, COUNT_BDB_LEDGER_GET_NEXT);
	}

	BerkeleySearchCursor getPrev(BerkeleySearchCursor cursor) {
		return withTime(() -> {
			try (var databaseCursor = toSecondaryCursor(cursor.getType())) {
				var pKey = entry(cursor.getPrimary());
				var key = entry(cursor.getIndex());

				return Optional.of(databaseCursor.getSearchBothRange(key, pKey, null, DEFAULT))
					.filter(status -> status == SUCCESS)
					.map(status -> databaseCursor.getPrevDup(key, pKey, null, DEFAULT))
					.filter(status -> status == SUCCESS)
					.map(status -> new BerkeleySearchCursor(this, cursor.getType(), pKey.getData(), key.getData()))
					.orElse(null);

			} catch (Exception ex) {
				throw new BerkeleyStoreException("Error while advancing cursor", ex);
			}
		}, ELAPSED_BDB_LEDGER_GET_PREV, COUNT_BDB_LEDGER_GET_PREV);
	}

	BerkeleySearchCursor getFirst(BerkeleySearchCursor cursor) {
		return withTime(() -> {
			try (var databaseCursor = toSecondaryCursor(cursor.getType())) {
				var pKey = entry(cursor.getPrimary());
				var key = entry(cursor.getIndex());

				if (databaseCursor.getSearchBothRange(key, pKey, null, DEFAULT) != SUCCESS) {
					return null;
				}

				if (databaseCursor.getPrevNoDup(key, pKey, null, DEFAULT) == SUCCESS) {
					if (databaseCursor.getNext(key, pKey, null, DEFAULT) == SUCCESS) {
						return new BerkeleySearchCursor(this, cursor.getType(), pKey.getData(), key.getData());
					}
				} else if (databaseCursor.getFirst(key, pKey, null, DEFAULT) == SUCCESS) {
					return new BerkeleySearchCursor(this, cursor.getType(), pKey.getData(), key.getData());
				}

				return null;
			} catch (Exception ex) {
				throw new BerkeleyStoreException("Error while advancing cursor", ex);
			}
		}, ELAPSED_BDB_LEDGER_GET_FIRST, COUNT_BDB_LEDGER_GET_FIRST);
	}

	BerkeleySearchCursor getLast(BerkeleySearchCursor cursor) {
		return withTime(() -> {
			try (var databaseCursor = toSecondaryCursor(cursor.getType())) {
				var pKey = entry(cursor.getPrimary());
				var key = entry(cursor.getIndex());

				if (databaseCursor.getSearchBothRange(key, pKey, null, DEFAULT) != SUCCESS) {
					return null;
				}

				if (databaseCursor.getNextNoDup(key, pKey, null, DEFAULT) == SUCCESS) {
					if (databaseCursor.getPrev(key, pKey, null, DEFAULT) == SUCCESS) {
						return new BerkeleySearchCursor(this, cursor.getType(), pKey.getData(), key.getData());
					}
				} else if (databaseCursor.getLast(key, pKey, null, DEFAULT) == SUCCESS) {
					return new BerkeleySearchCursor(this, cursor.getType(), pKey.getData(), key.getData());
				}

				return null;
			} catch (Exception ex) {
				throw new BerkeleyStoreException("Error while advancing cursor", ex);
			}
		}, ELAPSED_BDB_LEDGER_GET_LAST, COUNT_BDB_LEDGER_GET_LAST);
	}

	private SecondaryCursor toSecondaryCursor(com.sleepycat.je.Transaction tx, LedgerIndexType type) {
		switch (type) {
			case UNIQUE:
				return uniqueIndicesDatabase.openCursor(tx, null);
			case DUPLICATE:
				return duplicatedIndicesDatabase.openCursor(tx, null);
			default:
				throw new IllegalStateException("Cursor type " + type + " not supported");
		}
	}

	private CommittedAtom restoreCommittedAtom(byte[] data) throws DeserializeException {
		return serialization.fromDson(data, CommittedAtom.class);
	}

	private static void failIfNotSuccess(OperationStatus status, String message, Object object) {
		if (status != SUCCESS) {
			fail(message + " '" + object + "' failed with status " + status);
		}
	}

	private SecondaryCursor toSecondaryCursor(LedgerIndexType type) {
		return toSecondaryCursor(null, type);
	}

	static DatabaseEntry entry(byte[] data) {
		return new DatabaseEntry(data);
	}

	static AID getAidFromPKey(DatabaseEntry pKey) {
		return AID.from(pKey.getData(), Long.BYTES); // prefix + LC
	}

	private static DatabaseEntry entry() {
		return new DatabaseEntry();
	}

	private static DatabaseEntry toPKey(long logicalClock) {
		var pKey = new byte[Long.BYTES];
		Longs.copyTo(logicalClock, pKey, 0);
		return entry(pKey);
	}

	private static DatabaseEntry toPKey(long logicalClock, AID aid) {
		var pKey = new byte[Long.BYTES + AID.BYTES];
		Longs.copyTo(logicalClock, pKey, 0);
		System.arraycopy(aid.getBytes(), 0, pKey, Long.BYTES, AID.BYTES);
		return entry(pKey);
	}

	private void addTime(long start, CounterType detailTime, CounterType detailCounter) {
		final var elapsed = (System.nanoTime() - start + 500L) / 1000L;
		systemCounters.add(CounterType.ELAPSED_BDB_LEDGER_TOTAL, elapsed);
		systemCounters.increment(CounterType.COUNT_BDB_LEDGER_TOTAL);
		systemCounters.add(detailTime, elapsed);
		systemCounters.increment(detailCounter);
	}

	private void addBytesRead(DatabaseEntry entryA, DatabaseEntry entryB) {
		long amount = (long) entryA.getSize() + (long) entryB.getSize();
		systemCounters.add(CounterType.COUNT_BDB_LEDGER_BYTES_READ, amount);
	}

	private void addBytesWrite(DatabaseEntry entryA, DatabaseEntry entryB) {
		long amount = (long) entryA.getSize() + (long) entryB.getSize();
		systemCounters.add(CounterType.COUNT_BDB_LEDGER_BYTES_WRITE, amount);
	}

	private static com.sleepycat.je.Transaction unwrap(Transaction tx) {
		return Optional.ofNullable(tx)
			.map(wrapped -> tx.<com.sleepycat.je.Transaction>unwrap())
			.orElse(null);
	}
}
