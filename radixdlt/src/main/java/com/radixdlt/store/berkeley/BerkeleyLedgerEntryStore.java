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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.UnsignedBytes;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.common.AID;
import com.radixdlt.store.SearchCursor;
import com.radixdlt.store.StoreIndex;
import com.radixdlt.store.StoreIndex.LedgerIndexType;
import com.radixdlt.store.LedgerSearchMode;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.store.LedgerEntry;
import com.radixdlt.consensus.tempo.TempoException;
import com.radixdlt.store.LedgerEntryConflict;
import com.radixdlt.store.LedgerEntryStoreResult;
import com.radixdlt.store.LedgerEntryStatus;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.utils.Longs;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.SecondaryMultiKeyCreator;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.UniqueConstraintException;
import org.radix.database.DatabaseEnvironment;
import org.radix.database.exceptions.DatabaseException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.shards.ShardRange;
import org.radix.shards.ShardSpace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.radixdlt.store.berkeley.LedgerEntryIndices.ENTRY_INDEX_PREFIX;
import static com.radixdlt.store.berkeley.LedgerEntryIndices.SHARD_INDEX_PREFIX;

@Singleton
public class BerkeleyLedgerEntryStore implements LedgerEntryStore {
	private static final Logger log = Logging.getLogger("store.atoms");

	private static final String ATOM_INDICES_DB_NAME = "tempo2.atom_indices";
	private static final String DUPLICATE_INDICES_DB_NAME = "tempo2.duplicated_indices";
	private static final String UNIQUE_INDICES_DB_NAME = "tempo2.unique_indices";
	private static final String PENDING_DB_NAME = "tempo2.pending";
	private static final String ATOMS_DB_NAME = "tempo2.atoms";

	private static final byte PREFIX_COMMITTED = 0b0000_0000;
	private static final byte PREFIX_PENDING = 0b0000_0001;

	private final Serialization serialization;
	private final DatabaseEnvironment dbEnv;

	private final AtomicLong pendingLogicalClock;
	private final Map<AID, LedgerEntryIndices> currentIndices = new ConcurrentHashMap<>();

	private Database atoms; // TempoAtoms by primary keys (logical clock + AID bytes, no prefixes)
	private SecondaryDatabase uniqueIndices; // TempoAtoms by secondary unique indices (with prefixes)
	private SecondaryDatabase duplicatedIndices; // TempoAtoms by secondary duplicate indices (with prefixes)
	private Database atomIndices; // TempoAtomIndices by same primary keys
	private Database pending; // AIDs marked as 'pending'

	@Inject
	public BerkeleyLedgerEntryStore(
		Serialization serialization,
		DatabaseEnvironment dbEnv
	) {
		this.serialization = Objects.requireNonNull(serialization);
		this.dbEnv = Objects.requireNonNull(dbEnv);

		this.open();

		// TODO is this LC persistence sufficient? might be reset if there are no pending at some point in time
		this.pendingLogicalClock = new AtomicLong(getLastPendingCursor());
	}

	private void open() {
		DatabaseConfig primaryConfig = new DatabaseConfig();
		primaryConfig.setAllowCreate(true);
		primaryConfig.setTransactional(true);
		primaryConfig.setKeyPrefixing(true);
		primaryConfig.setBtreeComparator(BerkeleyLedgerEntryStore.AtomStorePackedPrimaryKeyComparator.class);

		SecondaryConfig uniqueIndicesConfig = new SecondaryConfig();
		uniqueIndicesConfig.setAllowCreate(true);
		uniqueIndicesConfig.setTransactional(true);
		uniqueIndicesConfig.setMultiKeyCreator(AtomSecondaryCreator.from(this.currentIndices, LedgerEntryIndices::getUniqueIndices));

		SecondaryConfig duplicateIndicesConfig = new SecondaryConfig();
		duplicateIndicesConfig.setAllowCreate(true);
		duplicateIndicesConfig.setTransactional(true);
		duplicateIndicesConfig.setSortedDuplicates(true);
		duplicateIndicesConfig.setMultiKeyCreator(AtomSecondaryCreator.from(this.currentIndices, LedgerEntryIndices::getDuplicateIndices));

		DatabaseConfig indicesConfig = new DatabaseConfig();
		indicesConfig.setAllowCreate(true);
		indicesConfig.setTransactional(true);
		indicesConfig.setBtreeComparator(BerkeleyLedgerEntryStore.AtomStorePackedPrimaryKeyComparator.class);

		DatabaseConfig pendingConfig = new DatabaseConfig();
		pendingConfig.setAllowCreate(true);
		pendingConfig.setTransactional(true);
		pendingConfig.setBtreeComparator(BerkeleyLedgerEntryStore.AtomStorePackedPrimaryKeyComparator.class);

		try {
			// This SuppressWarnings here is valid, as ownership of the underlying
			// resource is not changed here, the resource is just accessed.
			@SuppressWarnings("resource")
			Environment env = this.dbEnv.getEnvironment();
			this.atoms = env.openDatabase(null, ATOMS_DB_NAME, primaryConfig);
			this.uniqueIndices = env.openSecondaryDatabase(null, UNIQUE_INDICES_DB_NAME, this.atoms, uniqueIndicesConfig);
			this.duplicatedIndices = env.openSecondaryDatabase(null, DUPLICATE_INDICES_DB_NAME, this.atoms, duplicateIndicesConfig);
			this.atomIndices = env.openDatabase(null, ATOM_INDICES_DB_NAME, primaryConfig);
			this.pending = env.openDatabase(null, PENDING_DB_NAME, pendingConfig);
		} catch (Exception e) {
			throw new TempoException("Error while opening databases", e);
		}

		if (System.getProperty("db.check_integrity", "1").equals("1")) {
			// TODO implement integrity check
		}
	}

	@Override
	public void reset() {
		dbEnv.withLock(() -> {
			Transaction transaction = null;
			try {
				// This SuppressWarnings here is valid, as ownership of the underlying
				// resource is not changed here, the resource is just accessed.
				@SuppressWarnings("resource")
				Environment env = this.dbEnv.getEnvironment();
				transaction = env.beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
				env.truncateDatabase(transaction, ATOMS_DB_NAME, false);
				env.truncateDatabase(transaction, UNIQUE_INDICES_DB_NAME, false);
				env.truncateDatabase(transaction, DUPLICATE_INDICES_DB_NAME, false);
				env.truncateDatabase(transaction, ATOM_INDICES_DB_NAME, false);
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

				throw new TempoException("Error while resetting databases", e);
			}
		});
	}

	@Override
	public void close() {
		if (this.uniqueIndices != null) {
			this.uniqueIndices.close();
		}
		if (this.duplicatedIndices != null) {
			this.duplicatedIndices.close();
		}
		if (this.atoms != null) {
			this.atoms.close();
		}
		if (this.atomIndices != null) {
			this.atomIndices.close();
		}
		if (this.pending != null) {
			this.pending.close();
		}
	}

	private void fail(String message) {
		log.error(message);
		throw new TempoException(message);
	}

	private void fail(String message, Exception cause) {
		log.error(message, cause);
		throw new TempoException(message, cause);
	}

	@Override
	public boolean contains(AID aid) {
		DatabaseEntry key = new DatabaseEntry(StoreIndex.from(ENTRY_INDEX_PREFIX, aid.getBytes()));
		return OperationStatus.SUCCESS == this.uniqueIndices.get(null, key, null, LockMode.DEFAULT);
	}

	@Override
	public LedgerEntryStatus getStatus(AID aid) {
		if (!contains(aid)) {
			return LedgerEntryStatus.UNAVAILABLE;
		}

		if (isPending(aid)) {
			return LedgerEntryStatus.PENDING;
		} else {
			return LedgerEntryStatus.COMMITTED;
		}
	}

	private boolean isPending(AID aid) {
			DatabaseEntry key = new DatabaseEntry(aid.getBytes());
			return OperationStatus.SUCCESS == this.pending.get(null, key, null, LockMode.DEFAULT);
	}

	@Override
	public Optional<LedgerEntry> get(AID aid) {
		try {
			DatabaseEntry key = new DatabaseEntry(StoreIndex.from(ENTRY_INDEX_PREFIX, aid.getBytes()));
			DatabaseEntry value = new DatabaseEntry();

			if (this.uniqueIndices.get(null, key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				return Optional.of(serialization.fromDson(value.getData(), LedgerEntry.class));
			}
		} catch (Exception e) {
			fail("Get of atom '" + aid + "' failed", e);
		}

		return Optional.empty();
	}

	@Override
	public Set<StoreIndex> getUniqueIndices(AID aid) {
		try {
			return doGetIndices(null, aid, new DatabaseEntry()).getUniqueIndices();
		} catch (SerializationException e) {
			fail("Get unique indices of '" + aid + "' failed");
		}
		throw new IllegalStateException("Should never reach here");
	}

	@Override
	public void commit(AID aid) {
		// delete from pending and move to committed
		Transaction transaction = dbEnv.getEnvironment().beginTransaction(null, null);
		try {
			// TODO there must be a better way to change primary keys
			DatabaseEntry pKey = new DatabaseEntry();
			LedgerEntryIndices indices = doGetIndices(transaction, aid, pKey);
			DatabaseEntry value = new DatabaseEntry();
			OperationStatus status = atoms.get(transaction, pKey, value, LockMode.DEFAULT);
			if (status != OperationStatus.SUCCESS) {
				fail("Getting pending atom '" + aid + "' failed with status " + status);
			}
			if (!doDelete(aid, transaction, pKey, indices)) {
				fail("Delete of pending atom '" + aid + "' failed");
			}
			doRemovePending(aid, transaction);

			long logicalClock = lcFromPKey(pKey.getData());
			// transaction is aborted in doStore in case of conflict
			LedgerEntryStoreResult result = doStore(PREFIX_COMMITTED, logicalClock, aid, value.getData(), indices, transaction);
			if (result.isSuccess()) {
				transaction.commit();
			}
		} catch (Exception e) {
			transaction.abort();
			fail("Commit of pending atom '" + aid + "' failed", e);
		}
	}

	@Override
	public LedgerEntryStoreResult store(LedgerEntry atom, Set<StoreIndex> uniqueIndices, Set<StoreIndex> duplicateIndices) {
		Transaction transaction = dbEnv.getEnvironment().beginTransaction(null, null);
		try {
			// transaction is aborted in doStore in case of conflict
			LedgerEntryStoreResult result = doStorePending(atom, uniqueIndices, duplicateIndices, transaction);
			if (result.isSuccess()) {
				transaction.commit();
			}
			return result;
		} catch (Exception e) {
			transaction.abort();
			fail("Store of atom '" + atom.getAID() + "' failed", e);
		}
		throw new IllegalStateException("Should never reach here");
	}

	@Override
	public LedgerEntryStoreResult replace(Set<AID> aids, LedgerEntry atom, Set<StoreIndex> uniqueIndices, Set<StoreIndex> duplicateIndices) {
		Transaction transaction = dbEnv.getEnvironment().beginTransaction(null, null);
		try {
			for (AID aid : aids) {
				if (!doDelete(aid, transaction)) {
					transaction.abort();
					fail("Could not delete '" + aid + "'");
				}
			}
			// transaction is aborted in doStore in case of conflict
			LedgerEntryStoreResult result = doStorePending(atom, uniqueIndices, duplicateIndices, transaction);
			if (result.isSuccess()) {
				transaction.commit();
			}
			return result;
		} catch (Exception e) {
			transaction.abort();
			fail("Replace of atoms '" + aids + "' with atom '" + atom.getAID() + "' failed", e);
		}
		throw new IllegalStateException("Should never reach here");
	}

	private LedgerEntryStoreResult doStorePending(
		LedgerEntry atom,
		Set<StoreIndex> uniqueIndices,
		Set<StoreIndex> duplicateIndices,
		Transaction transaction
	) throws SerializationException {
		byte[] atomData = serialization.toDson(atom, Output.PERSIST);
		LedgerEntryIndices indices = LedgerEntryIndices.from(atom, uniqueIndices, duplicateIndices);
		// TODO should probably do some ordering on pending atoms
		long pendingLC = pendingLogicalClock.incrementAndGet();
		doAddPending(atom.getAID(), pendingLC, transaction);
		return doStore(PREFIX_PENDING, pendingLC, atom.getAID(), atomData, indices, transaction);
	}

	private LedgerEntryStoreResult doStore(
		byte prefix,
		long logicalClock,
		AID aid,
		byte[] ledgerEntryData,
		LedgerEntryIndices indices,
		Transaction transaction
	) throws SerializationException {
		try {
			DatabaseEntry pKey = toPKey(prefix, logicalClock, aid);
			DatabaseEntry pData = new DatabaseEntry(ledgerEntryData);

			// put indices in temporary map for key creator to pick up
			this.currentIndices.put(aid, indices);
			OperationStatus status = this.atoms.putNoOverwrite(transaction, pKey, pData);
			if (status != OperationStatus.SUCCESS) {
				fail("Atom write for '" + aid + "' failed with status " + status);
			}

			DatabaseEntry indicesData = new DatabaseEntry(serialization.toDson(indices, Output.PERSIST));
			status = this.atomIndices.putNoOverwrite(transaction, pKey, indicesData);
			if (status != OperationStatus.SUCCESS) {
				fail("LedgerEntry indices write for '" + aid + "' failed with status " + status);
			}
		} catch (UniqueConstraintException e) {
			log.error("Unique indices of ledgerEntry '" + aid + "' are in conflict, aborting transaction");
			transaction.abort();

			LedgerEntry ledgerEntry = serialization.fromDson(ledgerEntryData, LedgerEntry.class);
			ImmutableMap<StoreIndex, LedgerEntry> conflictingAtoms = doGetConflictingAtoms(indices.getUniqueIndices(), null);
			return LedgerEntryStoreResult.conflict(new LedgerEntryConflict(ledgerEntry, conflictingAtoms));
		} finally {
			this.currentIndices.remove(aid);
		}
		return LedgerEntryStoreResult.success();
	}

	private ImmutableMap<StoreIndex, LedgerEntry> doGetConflictingAtoms(Set<StoreIndex> uniqueIndices, Transaction transaction) {
		ImmutableMap.Builder<StoreIndex, LedgerEntry> conflictingAtoms = ImmutableMap.builder();
		try {
			DatabaseEntry key = new DatabaseEntry();
			DatabaseEntry value = new DatabaseEntry();
			for (StoreIndex uniqueIndex : uniqueIndices) {
				key.setData(uniqueIndex.asKey());
				if (this.uniqueIndices.get(transaction, key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					LedgerEntry conflictingAtom = serialization.fromDson(value.getData(), LedgerEntry.class);
					conflictingAtoms.put(uniqueIndex, conflictingAtom);
				}
			}
		} catch (Exception e) {
			fail(String.format("Failed getting conflicting atom for unique indices %s: '%s'",
				uniqueIndices.stream()
					.map(StoreIndex::toHexString)
					.collect(Collectors.joining(", ")),
				e.toString()), e);
		}

		return conflictingAtoms.build();
	}

	private boolean doDelete(AID aid, Transaction transaction) throws SerializationException {
		if (!isPending(aid)) {
			fail("Attempted to delete committed atom '" + aid + "'");
		}

		DatabaseEntry pKey = new DatabaseEntry();
		LedgerEntryIndices indices = doGetIndices(transaction, aid, pKey);
		return doDelete(aid, transaction, pKey, indices);
	}

	private boolean doDelete(AID aid, Transaction transaction, DatabaseEntry pKey, LedgerEntryIndices indices) {
		try {
			OperationStatus status = atomIndices.delete(transaction, pKey);
			if (status != OperationStatus.SUCCESS) {
				fail("Deleting indices of atom '" + aid + "' failed with status " + status);
			}
			currentIndices.put(aid, indices);
			return atoms.delete(transaction, pKey) == OperationStatus.SUCCESS;
		} finally {
			currentIndices.remove(aid);
		}
	}

	private LedgerEntryIndices doGetIndices(Transaction transaction, AID aid, DatabaseEntry pKey) throws SerializationException {
		DatabaseEntry key = new DatabaseEntry(StoreIndex.from(ENTRY_INDEX_PREFIX, aid.getBytes()));
		DatabaseEntry value = new DatabaseEntry();

		OperationStatus status = uniqueIndices.get(transaction, key, pKey, value, LockMode.DEFAULT);
		if (status != OperationStatus.SUCCESS) {
			fail("Getting primary key of atom '" + aid + "' failed with status " + status);
		}

		status = atomIndices.get(transaction, pKey, value, LockMode.DEFAULT);
		if (status != OperationStatus.SUCCESS) {
			fail("Getting indices of atom '" + aid + "' failed with status " + status);
		}

		return serialization.fromDson(value.getData(), LedgerEntryIndices.class);
	}

	private void doAddPending(AID aid, long pendingLC, Transaction transaction) {
		DatabaseEntry key = new DatabaseEntry(aid.getBytes());
		// TODO anything more useful that could be used as value for pending markers?
		DatabaseEntry value = new DatabaseEntry(Longs.toByteArray(pendingLC));
		pending.putNoOverwrite(transaction, key, value);
	}

	private void doRemovePending(AID aid, Transaction transaction) {
		OperationStatus status = pending.delete(transaction, new DatabaseEntry(aid.getBytes()));
		if (status != OperationStatus.SUCCESS) {
			fail("Removing atom '" + aid + "' from pending failed with status " + status);
		}
	}

	// TODO missing shardspace check, should be added?
	@Override
	public ImmutableList<AID> getNextCommitted(long logicalClock, int limit) {
		try (Cursor cursor = this.atoms.openCursor(null, null)) {
			ImmutableList.Builder<AID> aids = ImmutableList.builder();
			DatabaseEntry search = toPKey(PREFIX_COMMITTED, logicalClock + 1);
			OperationStatus status = cursor.getSearchKeyRange(search, null, LockMode.DEFAULT);

			int size = 0;
			while (status == OperationStatus.SUCCESS && size < limit) {
				if (search.getData()[0] != PREFIX_COMMITTED) {
					// if we've gone behind committed keys, abort, as this is only for committed atoms
					break;
				}

				aids.add(getAidFromPKey(search));
				status = cursor.getNext(search, null, LockMode.DEFAULT);
				size++;
			}

			return aids.build();
		}
	}
	@Override
	public SearchCursor search(LedgerIndexType type, StoreIndex index, LedgerSearchMode mode) {
		Objects.requireNonNull(type, "type is required");
		Objects.requireNonNull(index, "index is required");
		Objects.requireNonNull(mode, "mode is required");
		try (SecondaryCursor databaseCursor = toSecondaryCursor(type)) {
			DatabaseEntry pKey = new DatabaseEntry();
			DatabaseEntry key = new DatabaseEntry(index.asKey());
			if (mode == LedgerSearchMode.EXACT) {
				if (databaseCursor.getSearchKey(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return new BerkeleySearchCursor(this, type, pKey.getData(), key.getData());
				}
			} else if (mode == LedgerSearchMode.RANGE) {
				if (databaseCursor.getSearchKeyRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return new BerkeleySearchCursor(this, type, pKey.getData(), key.getData());
				}
			}

			return null;
		}
	}

	@Override
	public boolean contains(LedgerIndexType type, StoreIndex index, LedgerSearchMode mode) {
		Objects.requireNonNull(type, "type is required");
		Objects.requireNonNull(index, "index is required");
		Objects.requireNonNull(mode, "mode is required");
		try (SecondaryCursor databaseCursor = toSecondaryCursor(type)) {
			DatabaseEntry pKey = new DatabaseEntry();
			DatabaseEntry key = new DatabaseEntry(index.asKey());
			if (mode == LedgerSearchMode.EXACT) {
				if (databaseCursor.getSearchKey(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return true;
				}
			} else if (mode == LedgerSearchMode.RANGE) {
				if (databaseCursor.getSearchKeyRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return true;
				}
			}

			return false;
		}
	}

	@Override
	public Set<AID> getPending() {
		ImmutableSet.Builder<AID> pendingAids = ImmutableSet.builder();
		try (com.sleepycat.je.Cursor cursor = this.pending.openCursor(null, null)) {
			DatabaseEntry pKey = new DatabaseEntry();
			OperationStatus status = cursor.getFirst(pKey, null, LockMode.DEFAULT);
			while (status == OperationStatus.SUCCESS) {
				AID aid = AID.from(pKey.getData());
				pendingAids.add(aid);
				status = cursor.getNext(pKey, null, LockMode.DEFAULT);
			}
		}
		return pendingAids.build();
	}

	private long getLastPendingCursor() {
		try (com.sleepycat.je.Cursor cursor = this.pending.openCursor(null, null)) {
			DatabaseEntry pKey = new DatabaseEntry();
			DatabaseEntry value = new DatabaseEntry();
			OperationStatus status = cursor.getLast(pKey, value, LockMode.DEFAULT);
			if (status == OperationStatus.SUCCESS) {
				return Longs.fromByteArray(value.getData());
			} else {
				return 0L;
			}
		}
	}

	private Set<AID> dumpAll() {
		ImmutableSet.Builder<AID> pendingAids = ImmutableSet.builder();
		try (com.sleepycat.je.Cursor cursor = this.atoms.openCursor(null, null)) {
			DatabaseEntry pKey = new DatabaseEntry();
			OperationStatus status = cursor.getFirst(pKey, null, LockMode.DEFAULT);
			while (status == OperationStatus.SUCCESS) {
				AID aid = getAidFromPKey(pKey);
				pendingAids.add(aid);
				status = cursor.getNext(pKey, null, LockMode.DEFAULT);
			}
		}
		return pendingAids.build();
	}

	// not used yet
	private List<AID> getByShardChunkAndRange(int chunk, ShardRange range) throws DatabaseException {
		try {
			long from = ShardSpace.fromChunk(chunk, ShardSpace.SHARD_CHUNK_HALF_RANGE) + range.getLow();
			long to = from + range.getSpan();

			return this.getByShardRange(from, to);
		} catch (Exception ex) {
			throw new DatabaseException("While querying shard chunk and range", ex);
		}
	}

	// not used yet
	private List<AID> getByShardRange(long from, long to) throws DatabaseException {
		try (SecondaryCursor cursor = this.duplicatedIndices.openCursor(null, null)) {
			List<AID> aids = new ArrayList<>();
			DatabaseEntry key = new DatabaseEntry(StoreIndex.from(SHARD_INDEX_PREFIX, Longs.toByteArray(from)));
			DatabaseEntry pKey = new DatabaseEntry();
			DatabaseEntry value = new DatabaseEntry();

			OperationStatus status = cursor.getSearchKeyRange(key, value, pKey, LockMode.DEFAULT);
			while (status == OperationStatus.SUCCESS) {
				long shard = Longs.fromByteArray(key.getData(), 1);
				if (shard < from || shard > to) {
					break;
				}

				AID aid = getAidFromPKey(pKey);
				aids.add(aid);

				status = cursor.getNextDup(key, value, pKey, LockMode.DEFAULT);
				if (status == OperationStatus.NOTFOUND) {
					status = cursor.getNext(key, value, pKey, LockMode.DEFAULT);
				}
			}

			return aids;
		} catch (Exception ex) {
			throw new DatabaseException("While querying shard range", ex);
		}
	}

	// not used yet
	private List<AID> getByShard(long shard) throws DatabaseException {
		try (SecondaryCursor cursor = this.duplicatedIndices.openCursor(null, null)) {
			List<AID> aids = new ArrayList<>();

			DatabaseEntry key = new DatabaseEntry(StoreIndex.from(SHARD_INDEX_PREFIX, Longs.toByteArray(shard)));
			DatabaseEntry pKey = new DatabaseEntry();
			DatabaseEntry value = new DatabaseEntry();

			OperationStatus status = cursor.getSearchKeyRange(key, value, pKey, LockMode.DEFAULT);
			while (status == OperationStatus.SUCCESS) {
				AID aid = getAidFromPKey(pKey);
				aids.add(aid);
				status = cursor.getNextDup(key, value, pKey, LockMode.DEFAULT);
			}

			return aids;
		} catch (Exception ex) {
			throw new DatabaseException("While querying shard", ex);
		}
	}
	BerkeleySearchCursor getNext(BerkeleySearchCursor cursor) {
		try (SecondaryCursor databaseCursor = toSecondaryCursor(cursor.getType())) {
			DatabaseEntry pKey = new DatabaseEntry(cursor.getPrimary());
			DatabaseEntry key = new DatabaseEntry(cursor.getIndex());
			if (databaseCursor.getSearchBothRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				if (databaseCursor.getNextDup(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return new BerkeleySearchCursor(this, cursor.getType(), pKey.getData(), key.getData());
				}
			}

			return null;
		} catch (Exception ex) {
			throw new TempoException("Error while advancing cursor", ex);
		}
	}

	BerkeleySearchCursor getPrev(BerkeleySearchCursor cursor) {
		try (SecondaryCursor databaseCursor = toSecondaryCursor(cursor.getType())) {
			DatabaseEntry pKey = new DatabaseEntry(cursor.getPrimary());
			DatabaseEntry key = new DatabaseEntry(cursor.getIndex());
			if (databaseCursor.getSearchBothRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				if (databaseCursor.getPrevDup(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return new BerkeleySearchCursor(this, cursor.getType(), pKey.getData(), key.getData());
				}
			}

			return null;
		} catch (Exception ex) {
			throw new TempoException("Error while advancing cursor", ex);
		}
	}

	BerkeleySearchCursor getFirst(BerkeleySearchCursor cursor) {
		try (SecondaryCursor databaseCursor = toSecondaryCursor(cursor.getType())) {
			DatabaseEntry pKey = new DatabaseEntry(cursor.getPrimary());
			DatabaseEntry key = new DatabaseEntry(cursor.getIndex());
			if (databaseCursor.getSearchBothRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				if (databaseCursor.getPrevNoDup(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					if (databaseCursor.getNext(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
						return new BerkeleySearchCursor(this, cursor.getType(), pKey.getData(), key.getData());
					}
				} else if (databaseCursor.getFirst(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return new BerkeleySearchCursor(this, cursor.getType(), pKey.getData(), key.getData());
				}
			}

			return null;
		} catch (Exception ex) {
			throw new TempoException("Error while advancing cursor", ex);
		}
	}

	BerkeleySearchCursor getLast(BerkeleySearchCursor cursor) {
		try (SecondaryCursor databaseCursor = toSecondaryCursor(cursor.getType())) {
			DatabaseEntry pKey = new DatabaseEntry(cursor.getPrimary());
			DatabaseEntry key = new DatabaseEntry(cursor.getIndex());

			if (databaseCursor.getSearchBothRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				if (databaseCursor.getNextNoDup(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					if (databaseCursor.getPrev(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
						return new BerkeleySearchCursor(this, cursor.getType(), pKey.getData(), key.getData());
					}
				} else if (databaseCursor.getLast(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return new BerkeleySearchCursor(this, cursor.getType(), pKey.getData(), key.getData());
				}
			}

			return null;
		} catch (Exception ex) {
			throw new TempoException("Error while advancing cursor", ex);
		}
	}

	private SecondaryCursor toSecondaryCursor(LedgerIndexType type) {
		if (type.equals(StoreIndex.LedgerIndexType.UNIQUE)) {
			return this.uniqueIndices.openCursor(null, null);
		} else if (type.equals(StoreIndex.LedgerIndexType.DUPLICATE)) {
			return this.duplicatedIndices.openCursor(null, null);
		} else {
			throw new IllegalStateException("Cursor type " + type + " not supported");
		}
	}

	private static AID getAidFromPKey(DatabaseEntry pKey) {
		return AID.from(pKey.getData(), Long.BYTES + 1); // prefix + LC
	}

	private static DatabaseEntry toPKey(byte prefix, long logicalClock) {
		byte[] pKey = new byte[1 + Long.BYTES];
		pKey[0] = prefix;
		Longs.copyTo(logicalClock, pKey, 1);
		return new DatabaseEntry(pKey);
	}

	private static DatabaseEntry toPKey(byte prefix, long logicalClock, AID aid) {
		byte[] pKey = new byte[1 + Long.BYTES + AID.BYTES];
		pKey[0] = prefix;
		Longs.copyTo(logicalClock, pKey, 1);
		System.arraycopy(aid.getBytes(), 0, pKey, Long.BYTES + 1, AID.BYTES);
		return new DatabaseEntry(pKey);
	}

	private static long lcFromPKey(byte[] pKey) {
		return Longs.fromByteArray(pKey, 1);
	}

	public static class AtomStorePackedPrimaryKeyComparator implements Comparator<byte[]> {
		private static final int RELEVANT_PREFIX_LENGTH = 1 + Long.BYTES;
		@Override
		public int compare(byte[] primary1, byte[] primary2) {
			for (int i = 0; i < RELEVANT_PREFIX_LENGTH; i++) {
				int compare = UnsignedBytes.compare(primary1[i], primary2[i]);
				if (compare != 0) {
					return compare;
				}
			}
			return 0;
		}
	}

	private static class AtomSecondaryCreator implements SecondaryMultiKeyCreator {
		private final Function<DatabaseEntry, Set<StoreIndex>> indexer;

		private AtomSecondaryCreator(Function<DatabaseEntry, Set<StoreIndex>> indexer) {
			this.indexer = Objects.requireNonNull(indexer, "indexer is required");
		}

		@Override
		public void createSecondaryKeys(SecondaryDatabase database, DatabaseEntry key, DatabaseEntry value, Set<DatabaseEntry> secondaries) {
			// key should be primary key where first 8 bytes is the long clock
			Set<StoreIndex> indices = indexer.apply(key);
			indices.forEach(index -> secondaries.add(new DatabaseEntry(index.asKey())));
		}

		private static AtomSecondaryCreator from(Map<AID, LedgerEntryIndices> atomIndices, Function<LedgerEntryIndices, Set<StoreIndex>> indexer) {
			return new AtomSecondaryCreator(
				key -> {
					LedgerEntryIndices ledgerEntryIndices = atomIndices.get(getAidFromPKey(key));
					if (ledgerEntryIndices == null) {
						throw new IllegalStateException("Indices for atom '" + Longs.fromByteArray(key.getData()) + "' not available");
					}
					return indexer.apply(ledgerEntryIndices);
				}
			);
		}
	}
}
