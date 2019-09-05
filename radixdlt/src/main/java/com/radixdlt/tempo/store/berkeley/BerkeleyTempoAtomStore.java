package com.radixdlt.tempo.store.berkeley;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.UnsignedBytes;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.radixdlt.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerIndex.LedgerIndexType;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.store.AtomConflict;
import com.radixdlt.tempo.store.AtomStoreResult;
import com.radixdlt.tempo.store.TempoAtomStatus;
import com.radixdlt.tempo.store.TempoAtomStore;
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
import org.bouncycastle.util.Arrays;
import org.radix.database.DatabaseEnvironment;
import org.radix.database.exceptions.DatabaseException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.shards.ShardRange;
import org.radix.shards.ShardSpace;
import org.radix.utils.SystemProfiler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.radixdlt.tempo.store.berkeley.TempoAtomIndices.ATOM_INDEX_PREFIX;
import static com.radixdlt.tempo.store.berkeley.TempoAtomIndices.SHARD_INDEX_PREFIX;

@Singleton
public class BerkeleyTempoAtomStore implements TempoAtomStore {
	private static final Logger log = Logging.getLogger("store.atoms");

	private static final String ATOM_INDICES_DB_NAME = "tempo2.atom_indices";
	private static final String DUPLICATE_INDICES_DB_NAME = "tempo2.duplicated_indices";
	private static final String UNIQUE_INDICES_DB_NAME = "tempo2.unique_indices";
	private static final String PENDING_DB_NAME = "tempo2.pending";
	private static final String ATOMS_DB_NAME = "tempo2.atoms";

	private static final long LC_PREFIX_PENDING = Long.MAX_VALUE;
	private static final byte[] LC_PREFIX_PENDING_BYTES = Longs.toByteArray(LC_PREFIX_PENDING);
	private static final byte[] EMPTY_DATA = new byte[0];

	private final EUID self;
	private final Serialization serialization;
	private final SystemProfiler profiler;
	private final DatabaseEnvironment dbEnv;

	private final Map<AID, TempoAtomIndices> currentIndices = new ConcurrentHashMap<>();

	private Database atoms; // TempoAtoms by primary keys (logical clock + AID bytes, no prefixes)
	private SecondaryDatabase uniqueIndices; // TempoAtoms by secondary unique indices (with prefixes)
	private SecondaryDatabase duplicatedIndices; // TempoAtoms by secondary duplicate indices (with prefixes)
	private Database atomIndices; // TempoAtomIndices by same primary keys
	private Database pending; // AIDs marked as 'pending'

	@Inject
	public BerkeleyTempoAtomStore(
		@Named("self") EUID self,
		Serialization serialization,
		SystemProfiler profiler,
		DatabaseEnvironment dbEnv
	) {
		this.self = Objects.requireNonNull(self);
		this.serialization = Objects.requireNonNull(serialization);
		this.profiler = Objects.requireNonNull(profiler);
		this.dbEnv = Objects.requireNonNull(dbEnv);

		this.open();
	}

	private void open() {
		DatabaseConfig primaryConfig = new DatabaseConfig();
		primaryConfig.setAllowCreate(true);
		primaryConfig.setTransactional(true);
		primaryConfig.setKeyPrefixing(true);
		primaryConfig.setBtreeComparator(BerkeleyTempoAtomStore.AtomStorePackedPrimaryKeyComparator.class);

		SecondaryConfig uniqueIndicesConfig = new SecondaryConfig();
		uniqueIndicesConfig.setAllowCreate(true);
		uniqueIndicesConfig.setTransactional(true);
		uniqueIndicesConfig.setMultiKeyCreator(AtomSecondaryCreator.from(this.currentIndices, TempoAtomIndices::getUniqueIndices));

		SecondaryConfig duplicateIndicesConfig = new SecondaryConfig();
		duplicateIndicesConfig.setAllowCreate(true);
		duplicateIndicesConfig.setTransactional(true);
		duplicateIndicesConfig.setSortedDuplicates(true);
		duplicateIndicesConfig.setMultiKeyCreator(AtomSecondaryCreator.from(this.currentIndices, TempoAtomIndices::getDuplicateIndices));

		DatabaseConfig indicesConfig = new DatabaseConfig();
		indicesConfig.setAllowCreate(true);
		indicesConfig.setTransactional(true);
		indicesConfig.setBtreeComparator(BerkeleyTempoAtomStore.AtomStorePackedPrimaryKeyComparator.class);

		DatabaseConfig pendingConfig = new DatabaseConfig();
		pendingConfig.setAllowCreate(true);
		pendingConfig.setTransactional(true);
		pendingConfig.setBtreeComparator(BerkeleyTempoAtomStore.AtomStorePackedPrimaryKeyComparator.class);

		try {
			Environment dbEnv = this.dbEnv.getEnvironment();
			this.atoms = dbEnv.openDatabase(null, ATOMS_DB_NAME, primaryConfig);
			this.uniqueIndices = dbEnv.openSecondaryDatabase(null, UNIQUE_INDICES_DB_NAME, this.atoms, uniqueIndicesConfig);
			this.duplicatedIndices = dbEnv.openSecondaryDatabase(null, DUPLICATE_INDICES_DB_NAME, this.atoms, duplicateIndicesConfig);
			this.atomIndices = dbEnv.openDatabase(null, ATOM_INDICES_DB_NAME, primaryConfig);
			this.pending = dbEnv.openDatabase(null, PENDING_DB_NAME, pendingConfig);
		} catch (Exception e) {
			throw new TempoException("Error while opening databases", e);
		}

		if (System.getProperty("db.check_integrity", "1").equals("1")) {
			// TODO implement integrity check
		}
	}

	@Override
	public void reset() {
		Transaction transaction = null;
		try {
			dbEnv.lock();

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
		} finally {
			dbEnv.unlock();
		}
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

	public boolean contains(long clock) {
		long start = profiler.begin();
		try {
			DatabaseEntry key = new DatabaseEntry(Longs.toByteArray(clock));
			if (this.atoms.get(null, key, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				return true;
			}
		} finally {
			profiler.incrementFrom("ATOM_STORE:CONTAINS:CLOCK", start);
		}

		return false;
	}

	@Override
	public boolean contains(AID aid) {
		long start = profiler.begin();
		try {
			DatabaseEntry key = new DatabaseEntry(LedgerIndex.from(ATOM_INDEX_PREFIX, aid.getBytes()));
			return OperationStatus.SUCCESS == this.uniqueIndices.get(null, key, null, LockMode.DEFAULT);
		} finally {
			profiler.incrementFrom("ATOM_STORE:CONTAINS:CLOCK", start);
		}
	}

	@Override
	public TempoAtomStatus getStatus(AID aid) {
		if (!contains(aid)) {
			return TempoAtomStatus.UNAVAILABLE;
		}

		if (isPending(aid)) {
			return TempoAtomStatus.PENDING;
		} else {
			return TempoAtomStatus.COMMITTED;
		}
	}

	private boolean isPending(AID aid) {
		long start = profiler.begin();
		try {
			DatabaseEntry key = new DatabaseEntry(aid.getBytes());
			return OperationStatus.SUCCESS == this.pending.get(null, key, null, LockMode.DEFAULT);
		} finally {
			profiler.incrementFrom("ATOM_STORE:CONTAINS:CLOCK", start);
		}
	}

	public Optional<AID> get(long logicalClock) {
		long start = profiler.begin();
		try (Cursor cursor = this.atoms.openCursor(null, null)) {
			DatabaseEntry key = new DatabaseEntry(Longs.toByteArray(logicalClock));
			DatabaseEntry value = new DatabaseEntry();
			OperationStatus status = cursor.getSearchKeyRange(key, value, LockMode.DEFAULT);

			if (this.atoms.get(null, key, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				return Optional.of(AID.from(key.getData(), Long.BYTES));
			}
		} finally {
			profiler.incrementFrom("ATOM_STORE:GET:CLOCK", start);
		}

		return Optional.empty();
	}

	@Override
	public Optional<TempoAtom> get(AID aid) {
		long start = profiler.begin();
		try {
			DatabaseEntry key = new DatabaseEntry(LedgerIndex.from(ATOM_INDEX_PREFIX, aid.getBytes()));
			DatabaseEntry value = new DatabaseEntry();

			if (this.uniqueIndices.get(null, key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				return Optional.of(serialization.fromDson(value.getData(), TempoAtom.class));
			}
		} catch (Exception e) {
			fail("Get of atom '" + aid + "' failed", e);
		} finally {
			profiler.incrementFrom("ATOM_STORE:GET:AID", start);
		}

		return Optional.empty();
	}

	@Override
	public Set<LedgerIndex> getUniqueIndices(AID aid) {
		try {
			return doGetIndices(null, aid, new DatabaseEntry()).getUniqueIndices();
		} catch (SerializationException e) {
			fail("Get unique indices of '" + aid + "' failed");
		}
		throw new IllegalStateException("Should never reach here");
	}

	@Override
	public void commit(AID aid, long logicalClock) {
		// delete from pending and move to committed
		long start = profiler.begin();

		Transaction transaction = dbEnv.getEnvironment().beginTransaction(null, null);
		try {
			// TODO there must be a better way to change primary keys
			DatabaseEntry pKey = new DatabaseEntry();
			TempoAtomIndices indices = doGetIndices(transaction, aid, pKey);
			DatabaseEntry value = new DatabaseEntry();
			OperationStatus status = atoms.get(transaction, pKey, value, LockMode.DEFAULT);
			if (status != OperationStatus.SUCCESS) {
				fail("Getting pending atom '" + aid + "' failed with status " + status);
			}
			if (!doDelete(aid, transaction, pKey, indices)) {
				fail("Delete of pending atom '" + aid + "' failed");
			}
			doRemovePending(aid, transaction);

			// transaction is aborted in doStore in case of conflict
			AtomStoreResult result = doStore(logicalClock, aid, value.getData(), indices, transaction);
			if (result.isSuccess()) {
				transaction.commit();
			}
		} catch (Exception e) {
			transaction.abort();
			fail("Commit of pending atom '" + aid + "' failed", e);
		} finally {
			profiler.incrementFrom("ATOM_STORE:COMMIT", start);
		}
	}

	@Override
	public AtomStoreResult store(TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		long start = profiler.begin();
		Transaction transaction = dbEnv.getEnvironment().beginTransaction(null, null);
		try {
			// transaction is aborted in doStore in case of conflict
			AtomStoreResult result = doStorePending(atom, uniqueIndices, duplicateIndices, transaction);
			if (result.isSuccess()) {
				transaction.commit();
			}
			return result;
		} catch (Exception e) {
			transaction.abort();
			fail("Store of atom '" + atom.getAID() + "' failed", e);
		} finally {
			profiler.incrementFrom("ATOM_STORE:STORE", start);
		}
		throw new IllegalStateException("Should never reach here");
	}

	@Override
	public AtomStoreResult replace(Set<AID> aids, TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		long start = profiler.begin();
		Transaction transaction = dbEnv.getEnvironment().beginTransaction(null, null);
		try {
			for (AID aid : aids) {
				if (!doDelete(aid, transaction)) {
					transaction.abort();
					fail("Could not delete '" + aid + "'");
				}
			}
			// transaction is aborted in doStore in case of conflict
			AtomStoreResult result = doStorePending(atom, uniqueIndices, duplicateIndices, transaction);
			if (result.isSuccess()) {
				transaction.commit();
			}
			return result;
		} catch (Exception e) {
			transaction.abort();
			fail("Replace of atoms '" + aids + "' with atom '" + atom.getAID() + "' failed", e);
		} finally {
			profiler.incrementFrom("ATOM_STORE:REPLACE", start);
		}
		throw new IllegalStateException("Should never reach here");
	}

	private AtomStoreResult doStorePending(TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices, Transaction transaction) throws SerializationException {
		doAddPending(atom.getAID(), transaction);

		byte[] atomData = serialization.toDson(atom, Output.PERSIST);
		TempoAtomIndices indices = TempoAtomIndices.from(atom, uniqueIndices, duplicateIndices);
		// TODO should probably do some ordering on pending atoms
		return doStore(LC_PREFIX_PENDING, atom.getAID(), atomData, indices, transaction);
	}

	private AtomStoreResult doStore(long logicalClock, AID aid, byte[] atomData, TempoAtomIndices indices, Transaction transaction) throws SerializationException {
		try {
			DatabaseEntry pKey = new DatabaseEntry(Arrays.concatenate(Longs.toByteArray(logicalClock), aid.getBytes()));
			DatabaseEntry pData = new DatabaseEntry(atomData);

			// put indices in temporary map for key creator to pick up
			this.currentIndices.put(aid, indices);
			OperationStatus status = this.atoms.putNoOverwrite(transaction, pKey, pData);
			if (status != OperationStatus.SUCCESS) {
				fail("Atom write for '" + aid + "' failed with status " + status);
			}

			DatabaseEntry indicesData = new DatabaseEntry(serialization.toDson(indices, Output.PERSIST));
			status = this.atomIndices.putNoOverwrite(transaction, pKey, indicesData);
			if (status != OperationStatus.SUCCESS) {
				fail("Atom indices write for '" + aid + "' failed with status " + status);
			}
		} catch (UniqueConstraintException e) {
			log.error("Unique indices of atom '" + aid + "' are in conflict, aborting transaction");
			transaction.abort();

			Atom atom = serialization.fromDson(atomData, Atom.class);
			ImmutableMap<LedgerIndex, Atom> conflictingAtoms = doGetConflictingAtoms(indices.getUniqueIndices(), null);
			return AtomStoreResult.conflict(new AtomConflict(atom, conflictingAtoms));
		} finally {
			this.currentIndices.remove(aid);
		}
		return AtomStoreResult.success();
	}

	private ImmutableMap<LedgerIndex, Atom> doGetConflictingAtoms(Set<LedgerIndex> uniqueIndices, Transaction transaction) {
		ImmutableMap.Builder<LedgerIndex, Atom> conflictingAtoms = ImmutableMap.builder();
		try {
			DatabaseEntry key = new DatabaseEntry();
			DatabaseEntry value = new DatabaseEntry();
			for (LedgerIndex uniqueIndex : uniqueIndices) {
				key.setData(uniqueIndex.asKey());
				if (this.uniqueIndices.get(transaction, key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					Atom conflictingAtom = serialization.fromDson(value.getData(), TempoAtom.class);
					conflictingAtoms.put(uniqueIndex, conflictingAtom);
				}
			}
		} catch (Exception e) {
			fail(String.format("Failed getting conflicting atom for unique indices %s: '%s'",
				uniqueIndices.stream()
					.map(LedgerIndex::toHexString)
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
		TempoAtomIndices indices = doGetIndices(transaction, aid, pKey);
		return doDelete(aid, transaction, pKey, indices);
	}

	private boolean doDelete(AID aid, Transaction transaction, DatabaseEntry pKey, TempoAtomIndices indices) {
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

	private TempoAtomIndices doGetIndices(Transaction transaction, AID aid, DatabaseEntry pKey) throws SerializationException {
		DatabaseEntry key = new DatabaseEntry(LedgerIndex.from(ATOM_INDEX_PREFIX, aid.getBytes()));
		DatabaseEntry value = new DatabaseEntry();

		OperationStatus status = uniqueIndices.get(transaction, key, pKey, value, LockMode.DEFAULT);
		if (status != OperationStatus.SUCCESS) {
			fail("Getting primary key of atom '" + aid + "' failed with status " + status);
		}

		status = atomIndices.get(transaction, pKey, value, LockMode.DEFAULT);
		if (status != OperationStatus.SUCCESS) {
			fail("Getting indices of atom '" + aid + "' failed with status " + status);
		}

		return serialization.fromDson(value.getData(), TempoAtomIndices.class);
	}

	private void doAddPending(AID aid, Transaction transaction) {
		DatabaseEntry key = new DatabaseEntry(aid.getBytes());
		// TODO anything useful that could be used as value?
		DatabaseEntry value = new DatabaseEntry(EMPTY_DATA);
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
		long start = profiler.begin();
		try (Cursor cursor = this.atoms.openCursor(null, null)) {
			ImmutableList.Builder<AID> aids = ImmutableList.builder();
			DatabaseEntry search = new DatabaseEntry(Longs.toByteArray(logicalClock + 1));
			OperationStatus status = cursor.getSearchKeyRange(search, null, LockMode.DEFAULT);

			int size = 0;
			while (status == OperationStatus.SUCCESS && size < limit) {
				if (Longs.fromByteArray(search.getData()) == LC_PREFIX_PENDING) {
					// if we've reached the pending keys, abort, as this is only for committed atoms
					break;
				}

				aids.add(AID.from(search.getData(), Long.BYTES));
				status = cursor.getNext(search, null, LockMode.DEFAULT);
				size++;
			}

			return aids.build();
		} finally {
			profiler.incrementFrom("ATOM_STORE:DISCOVER:SYNC", start);
		}
	}

	@Override
	public LedgerCursor search(LedgerIndexType type, LedgerIndex index, LedgerSearchMode mode) {
		Objects.requireNonNull(type, "type is required");
		Objects.requireNonNull(index, "index is required");
		Objects.requireNonNull(mode, "mode is required");
		try (SecondaryCursor databaseCursor = toSecondaryCursor(type)) {
			DatabaseEntry pKey = new DatabaseEntry();
			DatabaseEntry key = new DatabaseEntry(index.asKey());
			if (mode == LedgerSearchMode.EXACT) {
				if (databaseCursor.getSearchKey(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return new BerkeleyCursor(this, type, pKey.getData(), key.getData());
				}
			} else if (mode == LedgerSearchMode.RANGE) {
				if (databaseCursor.getSearchKeyRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return new BerkeleyCursor(this, type, pKey.getData(), key.getData());
				}
			}

			return null;
		}
	}

	@Override
	public boolean contains(LedgerIndexType type, LedgerIndex index, LedgerSearchMode mode) {
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
		try (Cursor cursor = this.pending.openCursor(null, null)) {
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

	private Set<AID> dumpAll() {
		ImmutableSet.Builder<AID> pendingAids = ImmutableSet.builder();
		try (Cursor cursor = this.atoms.openCursor(null, null)) {
			DatabaseEntry pKey = new DatabaseEntry();
			OperationStatus status = cursor.getFirst(pKey, null, LockMode.DEFAULT);
			while (status == OperationStatus.SUCCESS) {
				AID aid = AID.from(pKey.getData(), Long.BYTES);
				pendingAids.add(aid);
				status = cursor.getNext(pKey, null, LockMode.DEFAULT);
			}
		}
		return pendingAids.build();
	}

	// not used yet
	private List<AID> getByShardChunkAndRange(int chunk, ShardRange range) throws DatabaseException {
		long start = profiler.begin();

		try {
			long from = ShardSpace.fromChunk(chunk, ShardSpace.SHARD_CHUNK_HALF_RANGE) + range.getLow();
			long to = from + range.getSpan();

			return this.getByShardRange(from, to);
		} catch (Exception ex) {
			throw new DatabaseException(ex);
		} finally {
			profiler.incrementFrom("ATOM_STORE:GET_BY_SHARD_CHUNK_AND_RANGE", start);
		}
	}

	// not used yet
	private List<AID> getByShardRange(long from, long to) throws DatabaseException {
		long start = profiler.begin();
		try (SecondaryCursor cursor = this.duplicatedIndices.openCursor(null, null)) {
			List<AID> aids = new ArrayList<>();
			DatabaseEntry key = new DatabaseEntry(LedgerIndex.from(SHARD_INDEX_PREFIX, Longs.toByteArray(from)));
			DatabaseEntry pKey = new DatabaseEntry();
			DatabaseEntry value = new DatabaseEntry();

			OperationStatus status = cursor.getSearchKeyRange(key, value, pKey, LockMode.DEFAULT);
			while (status == OperationStatus.SUCCESS) {
				long shard = Longs.fromByteArray(key.getData(), 1);
				if (shard < from || shard > to) {
					break;
				}

				AID aid = AID.from(pKey.getData(), Long.BYTES);
				aids.add(aid);

				status = cursor.getNextDup(key, value, pKey, LockMode.DEFAULT);
				if (status == OperationStatus.NOTFOUND) {
					status = cursor.getNext(key, value, pKey, LockMode.DEFAULT);
				}
			}

			return aids;
		} catch (Exception ex) {
			throw new DatabaseException(ex);
		} finally {
			profiler.incrementFrom("ATOM_STORE:GET_BY_SHARD_RANGE", start);
		}
	}

	// not used yet
	private List<AID> getByShard(long shard) throws DatabaseException {
		long start = profiler.begin();
		try (SecondaryCursor cursor = this.duplicatedIndices.openCursor(null, null)) {
			List<AID> aids = new ArrayList<>();

			DatabaseEntry key = new DatabaseEntry(LedgerIndex.from(SHARD_INDEX_PREFIX, Longs.toByteArray(shard)));
			DatabaseEntry pKey = new DatabaseEntry();
			DatabaseEntry value = new DatabaseEntry();

			OperationStatus status = cursor.getSearchKeyRange(key, value, pKey, LockMode.DEFAULT);
			while (status == OperationStatus.SUCCESS) {
				AID aid = AID.from(pKey.getData(), Long.BYTES);
				aids.add(aid);
				status = cursor.getNextDup(key, value, pKey, LockMode.DEFAULT);
			}

			return aids;
		} catch (Exception ex) {
			throw new DatabaseException(ex);
		} finally {
			profiler.incrementFrom("ATOM_STORE:GET_BY_SHARD", start);
		}
	}

	BerkeleyCursor getNext(BerkeleyCursor cursor) {
		try (SecondaryCursor databaseCursor = toSecondaryCursor(cursor.getType())) {
			DatabaseEntry pKey = new DatabaseEntry(cursor.getPrimary());
			DatabaseEntry key = new DatabaseEntry(cursor.getIndex());
			if (databaseCursor.getSearchBothRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				if (databaseCursor.getNextDup(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return new BerkeleyCursor(this, cursor.getType(), pKey.getData(), key.getData());
				}
			}

			return null;
		} catch (Exception ex) {
			throw new TempoException("Error while advancing cursor", ex);
		}
	}

	BerkeleyCursor getPrev(BerkeleyCursor cursor) {
		try (SecondaryCursor databaseCursor = toSecondaryCursor(cursor.getType())) {
			DatabaseEntry pKey = new DatabaseEntry(cursor.getPrimary());
			DatabaseEntry key = new DatabaseEntry(cursor.getIndex());
			if (databaseCursor.getSearchBothRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				if (databaseCursor.getPrevDup(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return new BerkeleyCursor(this, cursor.getType(), pKey.getData(), key.getData());
				}
			}

			return null;
		} catch (Exception ex) {
			throw new TempoException("Error while advancing cursor", ex);
		}
	}

	BerkeleyCursor getFirst(BerkeleyCursor cursor) {
		try (SecondaryCursor databaseCursor = toSecondaryCursor(cursor.getType())) {
			DatabaseEntry pKey = new DatabaseEntry(cursor.getPrimary());
			DatabaseEntry key = new DatabaseEntry(cursor.getIndex());
			if (databaseCursor.getSearchBothRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				if (databaseCursor.getPrevNoDup(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					if (databaseCursor.getNext(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
						return new BerkeleyCursor(this, cursor.getType(), pKey.getData(), key.getData());
					}
				} else if (databaseCursor.getFirst(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return new BerkeleyCursor(this, cursor.getType(), pKey.getData(), key.getData());
				}
			}

			return null;
		} catch (Exception ex) {
			throw new TempoException("Error while advancing cursor", ex);
		}
	}

	BerkeleyCursor getLast(BerkeleyCursor cursor) {
		try (SecondaryCursor databaseCursor = toSecondaryCursor(cursor.getType())) {
			DatabaseEntry pKey = new DatabaseEntry(cursor.getPrimary());
			DatabaseEntry key = new DatabaseEntry(cursor.getIndex());

			if (databaseCursor.getSearchBothRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				if (databaseCursor.getNextNoDup(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					if (databaseCursor.getPrev(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
						return new BerkeleyCursor(this, cursor.getType(), pKey.getData(), key.getData());
					}
				} else if (databaseCursor.getLast(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return new BerkeleyCursor(this, cursor.getType(), pKey.getData(), key.getData());
				}
			}

			return null;
		} catch (Exception ex) {
			throw new TempoException("Error while advancing cursor", ex);
		}
	}

	private SecondaryCursor toSecondaryCursor(LedgerIndexType type) {
		Objects.requireNonNull(type, "cursor is required");
		SecondaryCursor databaseCursor;
		if (type.equals(LedgerIndex.LedgerIndexType.UNIQUE)) {
			databaseCursor = this.uniqueIndices.openCursor(null, null);
		} else if (type.equals(LedgerIndex.LedgerIndexType.DUPLICATE)) {
			databaseCursor = this.duplicatedIndices.openCursor(null, null);
		} else {
			throw new IllegalStateException("Cursor type " + type + " not supported");
		}
		return databaseCursor;
	}

	public static class AtomStorePackedPrimaryKeyComparator implements Comparator<byte[]> {
		@Override
		public int compare(byte[] primary1, byte[] primary2) {
			int minLen = Math.min(primary1.length, primary2.length);
			for (int i = 0; i < minLen; i++) {
				int compare = UnsignedBytes.compare(primary1[i], primary2[i]);
				if (compare != 0) {
					return compare;
				}
			}
			return 0;
		}
	}

	private static class AtomSecondaryCreator implements SecondaryMultiKeyCreator {
		private final Function<DatabaseEntry, Set<LedgerIndex>> indexer;

		private AtomSecondaryCreator(Function<DatabaseEntry, Set<LedgerIndex>> indexer) {
			this.indexer = Objects.requireNonNull(indexer, "indexer is required");
		}

		@Override
		public void createSecondaryKeys(SecondaryDatabase database, DatabaseEntry key, DatabaseEntry value, Set<DatabaseEntry> secondaries) {
			// key should be primary key where first 8 bytes is the long clock
			Set<LedgerIndex> indices = indexer.apply(key);
			indices.forEach(index -> secondaries.add(new DatabaseEntry(index.asKey())));
		}

		private static AtomSecondaryCreator from(Map<AID, TempoAtomIndices> atomIndices, Function<TempoAtomIndices, Set<LedgerIndex>> indexer) {
			return new AtomSecondaryCreator(
				key -> {
					TempoAtomIndices tempoAtomIndices = atomIndices.get(AID.from(key.getData(), Long.BYTES));
					if (tempoAtomIndices == null) {
						throw new IllegalStateException("Indices for atom '" + Longs.fromByteArray(key.getData()) + "' not available");
					}
					return indexer.apply(tempoAtomIndices);
				}
			);
		}
	}
}
