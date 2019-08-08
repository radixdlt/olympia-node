package com.radixdlt.tempo.store;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.radixdlt.common.AID;
import com.radixdlt.common.Pair;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerCursor.Type;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.tempo.AtomStore;
import com.radixdlt.tempo.AtomStoreView;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.exceptions.TempoException;
import com.radixdlt.tempo.sync.IterativeCursor;
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
import org.bouncycastle.util.Arrays;
import org.radix.database.DatabaseEnvironment;
import org.radix.database.exceptions.DatabaseException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.shards.ShardRange;
import org.radix.shards.ShardSpace;
import org.radix.time.TemporalVertex;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.SystemProfiler;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class TempoAtomStore implements AtomStore {
	private static final Logger logger = Logging.getLogger("Store");

	private final Supplier<DatabaseEnvironment> databaseEnvironmentSupplier;
	private final AtomStoreViewAdapter view;
	private final Map<Long, TempoAtomIndices> atomIndices = new ConcurrentHashMap<>();

	private Database atomsDatabase;
	private SecondaryDatabase uniqueIndices;
	private SecondaryDatabase duplicatedIndices;

	public TempoAtomStore(Supplier<DatabaseEnvironment> databaseEnvironmentSupplier) {
		this.databaseEnvironmentSupplier = databaseEnvironmentSupplier;
		this.view = new AtomStoreViewAdapter(this);
	}

	@Override
	public void open() {
		DatabaseConfig primaryConfig = new DatabaseConfig();
		primaryConfig.setAllowCreate(true);
		primaryConfig.setTransactional(true);
		primaryConfig.setKeyPrefixing(true);
		primaryConfig.setBtreeComparator(TempoAtomStore.AtomStorePackedPrimaryKeyComparator.class);

		SecondaryConfig uniqueIndicesConfig = new SecondaryConfig();
		uniqueIndicesConfig.setAllowCreate(true);
		uniqueIndicesConfig.setTransactional(true);
		uniqueIndicesConfig.setMultiKeyCreator(new AtomMultipleSecondaryKeyCreator());

		SecondaryConfig duplicateIndicesConfig = new SecondaryConfig();
		duplicateIndicesConfig.setAllowCreate(true);
		duplicateIndicesConfig.setTransactional(true);
		duplicateIndicesConfig.setSortedDuplicates(true);
		duplicateIndicesConfig.setMultiKeyCreator(new AtomMultipleSecondaryKeyCreator());

		try {
			Environment dbEnv = databaseEnvironmentSupplier.get().getEnvironment();
			this.atomsDatabase = dbEnv.openDatabase(null, "tempo2.atoms", primaryConfig);
			this.uniqueIndices = dbEnv.openSecondaryDatabase(null, "tempo2.unique_indices", this.atomsDatabase, uniqueIndicesConfig);
			this.duplicatedIndices = dbEnv.openSecondaryDatabase(null, "tempo2.duplicated_indices", this.atomsDatabase, duplicateIndicesConfig);
		} catch (Exception e) {
			throw new TempoException("Error while opening database", e);
		}

		if (System.getProperty("db.check_integrity", "1").equals("1")) {
			integrity();
		}
	}

	@Override
	public void reset() {
		Transaction transaction = null;

		try {
			Modules.get(DatabaseEnvironment.class).lock();

			Environment dbEnv = databaseEnvironmentSupplier.get().getEnvironment();
			transaction = dbEnv.beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
			dbEnv.truncateDatabase(transaction, "tempo2.atoms", false);
			dbEnv.truncateDatabase(transaction, "tempo2.unique_indices", false);
			dbEnv.truncateDatabase(transaction, "tempo2.duplicated_indices", false);

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
			Modules.get(DatabaseEnvironment.class).unlock();
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
		if (this.atomsDatabase != null) {
			this.atomsDatabase.close();
		}
	}

	private void integrity() {
		// TODO implement
		// TODO require mechanism for LocalSystem to recover local state from ledger
	}

	@Override
	public AtomStoreView asReadOnlyView() {
		return this.view;
	}

	private void fail(String message) {
		logger.error(message);
		throw new TempoException(message);
	}

	private void fail(String message, Exception cause) {
		logger.error(message, cause);
		throw new TempoException(message, cause);
	}

	public boolean contains(long clock) {
		long start = SystemProfiler.getInstance().begin();

		try {
			DatabaseEntry key = new DatabaseEntry(Longs.toByteArray(clock));
			if (this.atomsDatabase.get(null, key, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				return true;
			}
		} finally {
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:CONTAINS:CLOCK", start);
		}

		return false;
	}

	@Override
	public boolean contains(AID aid) {
		long start = SystemProfiler.getInstance().begin();

		try {
			DatabaseEntry key = new DatabaseEntry(LedgerIndex.from(ATOM_INDEX_PREFIX, aid.getBytes()));
			return OperationStatus.SUCCESS == this.uniqueIndices.get(null, key, null, LockMode.DEFAULT);
		} finally {
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:CONTAINS:CLOCK", start);
		}
	}

	public Optional<TempoAtom> get(long clock) {
		long start = SystemProfiler.getInstance().begin();

		try {
			DatabaseEntry key = new DatabaseEntry(Longs.toByteArray(clock));
			DatabaseEntry value = new DatabaseEntry();

			if (this.atomsDatabase.get(null, key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				return Optional.of(Serialization.getDefault().fromDson(value.getData(), TempoAtom.class));
			}
		} catch (SerializationException e) {
			fail("Get of TempoAtom with clock " + clock + " failed", e);
		} finally {
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:GET:CLOCK", start);
		}

		return Optional.empty();
	}

	@Override
	public Optional<TempoAtom> get(AID aid) {
		long start = SystemProfiler.getInstance().begin();

		try {
			DatabaseEntry key = new DatabaseEntry(LedgerIndex.from(ATOM_INDEX_PREFIX, aid.getBytes()));
			DatabaseEntry value = new DatabaseEntry();

			if (this.uniqueIndices.get(null, key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				return Optional.of(Serialization.getDefault().fromDson(value.getData(), TempoAtom.class));
			}
		} catch (Exception e) {
			fail("Get of TempoAtom with AID " + aid + " failed", e);
		} finally {
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:GET:AID", start);
		}

		return Optional.empty();
	}

	@Override
	public boolean delete(AID aid) {
		long start = SystemProfiler.getInstance().begin();

		DatabaseEntry pKey = new DatabaseEntry();
		DatabaseEntry key = new DatabaseEntry(LedgerIndex.from(ATOM_INDEX_PREFIX, aid.getBytes()));
		Transaction transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, null);

		try {
			if (this.uniqueIndices.get(transaction, key, pKey, null, LockMode.RMW) == OperationStatus.SUCCESS) {
				if (this.atomsDatabase.delete(transaction, pKey) == OperationStatus.SUCCESS) {
					transaction.commit();
					return true;
				}
			}

		} catch (Exception e) {
			transaction.abort();
			fail("Delete of TempoAtom with AID " + aid + " failed", e);
		} finally {
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:DELETE_ATOM", start);
		}

		return false;
	}

	@Override
	public synchronized boolean store(TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		long start = SystemProfiler.getInstance().begin();

		TemporalVertex localTemporalVertex = atom.getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID());
		if (localTemporalVertex == null) {
			return false;
		}

		DatabaseEntry pKey = null;
		DatabaseEntry data = null;
		Transaction transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, null);

		try {
			OperationStatus status;
			pKey = new DatabaseEntry(Arrays.concatenate(Longs.toByteArray(localTemporalVertex.getClock()), atom.getAID().getBytes()));
			data = new DatabaseEntry(Serialization.getDefault().toDson(atom, Output.PERSIST));

			this.atomIndices.put(localTemporalVertex.getClock(), new TempoAtomIndices(atom, uniqueIndices, duplicateIndices));

			status = this.atomsDatabase.putNoOverwrite(transaction, pKey, data);
			if (!status.equals(OperationStatus.SUCCESS)) {
				return false;
			}

			transaction.commit();
			return true;
		} catch (Exception e) {
			transaction.abort();

			fail("Store of TempoAtom " + atom + " failed", e);

			// FIXME need to handle UniqueConstraintException

			return false;
		} finally {
			if (data != null && data.getData() != null && data.getData().length > 0) {
				this.atomIndices.remove(localTemporalVertex.getClock());
			}

			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:STORE", start);
		}
	}

	@Override
	// TODO make this properly Atomic
	public boolean replace(Set<AID> aids, TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		for (AID aid : aids) {
			this.delete(aid);
		}

		return this.store(atom, uniqueIndices, duplicateIndices);
	}

	// TODO we'll need some way to collect the AID -> shard information from the AtomStore
	// leaving in place for now, stubbed, will review later when implementing ShardChecksumSync
	public Set<AID> getByShardChunkAndRange(int chunk, ShardRange range) throws DatabaseException {
		long start = SystemProfiler.getInstance().begin();

		try {
			long from = ShardSpace.fromChunk(chunk, ShardSpace.SHARD_CHUNK_HALF_RANGE) + range.getLow();
			long to = from + range.getSpan();

			return this.getByShardRange(from, to);
		} catch (Exception ex) {
			throw new DatabaseException(ex);
		} finally {
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:GET_BY_SHARD_CHUNK_AND_RANGE", start);
		}
	}

	private Set<AID> getByShardRange(long from, long to) throws DatabaseException {
		long start = SystemProfiler.getInstance().begin();

		SecondaryCursor cursor = null;
		Set<AID> atomIds = new HashSet<>();

		try {
			cursor = this.duplicatedIndices.openCursor(null, null);

			DatabaseEntry key = new DatabaseEntry(LedgerIndex.from(SHARD_INDEX_PREFIX, Longs.toByteArray(from)));
			DatabaseEntry value = new DatabaseEntry();

			// FIXME this is slow as shit, speed it up, maybe pack clock+HID for primary and use that
			OperationStatus status = cursor.getSearchKeyRange(key, value, LockMode.DEFAULT);
			while (status == OperationStatus.SUCCESS) {
				long shard = Longs.fromByteArray(key.getData(), 1);
				if (shard < from || shard > to) {
					break;
				}

				TempoAtom atom = Serialization.getDefault().fromDson(value.getData(), TempoAtom.class);
				atomIds.add(atom.getAID());

				status = cursor.getNextDup(key, value, LockMode.DEFAULT);
				if (status == OperationStatus.NOTFOUND) {
					status = cursor.getNext(key, value, LockMode.DEFAULT);
				}
			}

			return atomIds;
		} catch (Exception ex) {
			throw new DatabaseException(ex);
		} finally {
			if (cursor != null) {
				cursor.close();
			}

			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:GET_BY_SHARD_RANGE", start);
		}
	}

	public Set<AID> getByShard(long shard) throws DatabaseException {
		long start = SystemProfiler.getInstance().begin();

		SecondaryCursor cursor = null;
		Set<AID> atomIds = new HashSet<>();

		try {
			cursor = this.duplicatedIndices.openCursor(null, null);

			DatabaseEntry key = new DatabaseEntry(LedgerIndex.from(SHARD_INDEX_PREFIX, Longs.toByteArray(shard)));
			DatabaseEntry value = new DatabaseEntry();

			// FIXME this is slow as shit, speed it up, maybe pack clock+HID for primary and use that
			OperationStatus status = cursor.getSearchKey(key, value, LockMode.DEFAULT);
			while (status == OperationStatus.SUCCESS) {
				TempoAtom atom = Serialization.getDefault().fromDson(value.getData(), TempoAtom.class);
				atomIds.add(atom.getAID());
				status = cursor.getNextDup(key, value, LockMode.DEFAULT);
			}

			return atomIds;
		} catch (Exception ex) {
			throw new DatabaseException(ex);
		} finally {
			if (cursor != null) {
				cursor.close();
			}

			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:GET_BY_SHARD", start);
		}
	}

	public boolean update(TempoAtom atom) {
		// FIXME I think we'll need this if we want to update the TemporalProof stored with an Atom after a conflict resolution

		return false;
	}

	// FIXME awful performance
	@Override
	public Pair<ImmutableList<AID>, IterativeCursor> getNext(IterativeCursor iterativeCursor, int limit, ShardSpace shardSpace) {
		List<AID> aids = Lists.newArrayList();
		long start = SystemProfiler.getInstance().begin();
		long position = iterativeCursor.getLogicalClockPosition();

		try (Cursor cursor = this.atomsDatabase.openCursor(null, null)) {
			// TODO remove position + 1 to someplace else
			DatabaseEntry search = new DatabaseEntry(Longs.toByteArray(position + 1));
			DatabaseEntry value = new DatabaseEntry();
			OperationStatus status = cursor.getSearchKeyRange(search, value, LockMode.DEFAULT);

			while (status == OperationStatus.SUCCESS) {
				TempoAtom atom = Serialization.getDefault().fromDson(value.getData(), TempoAtom.class);
				if (shardSpace.intersects(atom.getShards())) {
					position = Longs.fromByteArray(search.getData());
					aids.add(atom.getAID());

					if (aids.size() >= limit) {
						break;
					}
				}

				status = cursor.getNext(search, value, LockMode.DEFAULT);
			}
		} catch (SerializationException e) {
			throw new TempoException("Error while querying from database", e);
		} finally {
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:DISCOVER:SYNC", start);
		}

		IterativeCursor nextCursor = null;
		if (position != iterativeCursor.getLogicalClockPosition()) {
			nextCursor = new IterativeCursor(position, null);
		}

		return Pair.of(ImmutableList.copyOf(aids), new IterativeCursor(iterativeCursor.getLogicalClockPosition(), nextCursor));
	}

	public LedgerCursor search(Type type, LedgerIndex index, LedgerSearchMode mode) {
		Objects.requireNonNull(type, "type is required");
		Objects.requireNonNull(index, "index is required");
		Objects.requireNonNull(mode, "mode is required");

		SecondaryCursor databaseCursor;

		if (type == Type.UNIQUE) {
			databaseCursor = this.uniqueIndices.openCursor(null, null);
		} else if (type == Type.DUPLICATE) {
			databaseCursor = this.duplicatedIndices.openCursor(null, null);
		} else {
			throw new IllegalStateException("Type " + type + " not supported");
		}

		try {
			DatabaseEntry pKey = new DatabaseEntry();
			DatabaseEntry key = new DatabaseEntry(index.getKey());

			if (mode == LedgerSearchMode.EXACT) {
				if (databaseCursor.getSearchKey(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return new TempoCursor(this, type, pKey.getData(), key.getData());
				}
			} else if (mode == LedgerSearchMode.RANGE) {
				if (databaseCursor.getSearchKeyRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return new TempoCursor(this, type, pKey.getData(), key.getData());
				}
			}

			return null;
		} finally {
			databaseCursor.close();
		}
	}

	public TempoCursor getNext(TempoCursor cursor) throws DatabaseException {
		Objects.requireNonNull(cursor);

		SecondaryCursor databaseCursor;

		if (cursor.getType().equals(Type.UNIQUE)) {
			databaseCursor = this.uniqueIndices.openCursor(null, null);
		} else if (cursor.getType().equals(Type.DUPLICATE)) {
			databaseCursor = this.duplicatedIndices.openCursor(null, null);
		} else {
			throw new IllegalStateException("Type " + cursor.getType() + " not supported");
		}

		try {
			DatabaseEntry pKey = new DatabaseEntry(cursor.getPrimary());
			DatabaseEntry key = new DatabaseEntry(cursor.getIndex());

			if (databaseCursor.getSearchBothRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				if (databaseCursor.getNextDup(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return new TempoCursor(this, cursor.getType(), pKey.getData(), key.getData());
				}
			}

			return null;
		} catch (Exception ex) {
			throw new DatabaseException(ex);
		} finally {
			databaseCursor.close();
		}
	}

	public TempoCursor getPrev(TempoCursor cursor) throws DatabaseException {
		Objects.requireNonNull(cursor);

		SecondaryCursor databaseCursor;

		if (cursor.getType().equals(Type.UNIQUE)) {
			databaseCursor = this.uniqueIndices.openCursor(null, null);
		} else if (cursor.getType().equals(Type.DUPLICATE)) {
			databaseCursor = this.duplicatedIndices.openCursor(null, null);
		} else {
			throw new IllegalStateException("Type " + cursor.getType() + " not supported");
		}

		try {
			DatabaseEntry pKey = new DatabaseEntry(cursor.getPrimary());
			DatabaseEntry key = new DatabaseEntry(cursor.getIndex());

			if (databaseCursor.getSearchBothRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				if (databaseCursor.getPrevDup(pKey, key, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return new TempoCursor(this, cursor.getType(), pKey.getData(), key.getData());
				}
			}

			return null;
		} catch (Exception ex) {
			throw new DatabaseException(ex);
		} finally {
			databaseCursor.close();
		}
	}

	public TempoCursor getFirst(TempoCursor cursor) throws DatabaseException {
		Objects.requireNonNull(cursor);

		SecondaryCursor databaseCursor;

		if (cursor.getType().equals(Type.UNIQUE)) {
			databaseCursor = this.uniqueIndices.openCursor(null, null);
		} else if (cursor.getType().equals(Type.DUPLICATE)) {
			databaseCursor = this.duplicatedIndices.openCursor(null, null);
		} else {
			throw new IllegalStateException("Type " + cursor.getType() + " not supported");
		}

		try {
			DatabaseEntry pKey = new DatabaseEntry(cursor.getPrimary());
			DatabaseEntry key = new DatabaseEntry(cursor.getIndex());

			if (databaseCursor.getSearchBothRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				if (databaseCursor.getPrevNoDup(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					if (databaseCursor.getNext(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
						return new TempoCursor(this, cursor.getType(), pKey.getData(), key.getData());
					}
				} else if (databaseCursor.getFirst(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return new TempoCursor(this, cursor.getType(), pKey.getData(), key.getData());
				}
			}

			return null;
		} catch (Exception ex) {
			throw new DatabaseException(ex);
		} finally {
			databaseCursor.close();
		}
	}

	public TempoCursor getLast(TempoCursor cursor) throws DatabaseException {
		Objects.requireNonNull(cursor);

		SecondaryCursor databaseCursor;

		if (cursor.getType().equals(Type.UNIQUE)) {
			databaseCursor = this.uniqueIndices.openCursor(null, null);
		} else if (cursor.getType().equals(Type.DUPLICATE)) {
			databaseCursor = this.duplicatedIndices.openCursor(null, null);
		} else {
			throw new IllegalStateException("Type " + cursor.getType() + " not supported");
		}

		try {
			DatabaseEntry pKey = new DatabaseEntry(cursor.getPrimary());
			DatabaseEntry key = new DatabaseEntry(cursor.getIndex());

			if (databaseCursor.getSearchBothRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				if (databaseCursor.getNextNoDup(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					if (databaseCursor.getPrev(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
						return new TempoCursor(this, cursor.getType(), pKey.getData(), key.getData());
					}
				} else if (databaseCursor.getLast(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
					return new TempoCursor(this, cursor.getType(), pKey.getData(), key.getData());
				}
			}

			return null;
		} catch (Exception ex) {
			throw new DatabaseException(ex);
		} finally {
			databaseCursor.close();
		}
	}

	public static class AtomStorePackedPrimaryKeyComparator implements Comparator<byte[]> {
		@Override
		public int compare(byte[] primary1, byte[] primary2) {
			for (int b = 0; b < Long.BYTES; b++) {
				if (primary1[b] < primary2[b]) {
					return -1;
				} else if (primary1[b] > primary2[b]) {
					return 1;
				}
			}

			return 0;
		}
	}

	public static final byte ATOM_INDEX_PREFIX = 0;
	public static final byte SHARD_INDEX_PREFIX = 1;
	public static final byte DESTINATION_INDEX_PREFIX = 2;

	private final class TempoAtomIndices {
		private final Set<LedgerIndex> uniqueIndices;
		private final Set<LedgerIndex> duplicateIndices;

		TempoAtomIndices(TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
			for (LedgerIndex uniqueIndex : uniqueIndices) {
				if (uniqueIndex.getPrefix() == ATOM_INDEX_PREFIX ||
					uniqueIndex.getPrefix() == SHARD_INDEX_PREFIX ||
					uniqueIndex.getPrefix() == DESTINATION_INDEX_PREFIX) {
					throw new IllegalArgumentException("Unique indices for " + atom.getAID() + " contains Tempo restricted prefixes");
				}
			}

			for (LedgerIndex duplicateIndex : duplicateIndices) {
				if (duplicateIndex.getPrefix() == ATOM_INDEX_PREFIX ||
					duplicateIndex.getPrefix() == SHARD_INDEX_PREFIX ||
					duplicateIndex.getPrefix() == DESTINATION_INDEX_PREFIX) {
					throw new IllegalArgumentException("Duplicate indices for " + atom.getAID() + " contains Tempo restricted prefixes");
				}
			}

			this.uniqueIndices = new HashSet<>(uniqueIndices);
			this.uniqueIndices.add(new LedgerIndex(ATOM_INDEX_PREFIX, atom.getAID().getBytes()));

			this.duplicateIndices = new HashSet<>(duplicateIndices);
			for (Long shard : atom.getShards()) {
				this.duplicateIndices.add(new LedgerIndex(SHARD_INDEX_PREFIX, Longs.toByteArray(shard)));
			}
		}

		Set<LedgerIndex> getUniqueIndices() {
			return this.uniqueIndices;
		}

		Set<LedgerIndex> getDuplicateIndices() {
			return this.duplicateIndices;
		}
	}

	private class AtomMultipleSecondaryKeyCreator implements SecondaryMultiKeyCreator {
		@Override
		public void createSecondaryKeys(SecondaryDatabase database, DatabaseEntry key, DatabaseEntry value, Set<DatabaseEntry> secondaries) {
			// key should be primary key where first 8 bytes is the long clock
			TempoAtomIndices tempoAtomIndices = TempoAtomStore.this.atomIndices.get(Longs.fromByteArray(key.getData()));
			if (tempoAtomIndices == null) {
				throw new IllegalStateException("Indices for Atom@" + Longs.fromByteArray(key.getData()) + " not available");
			}

			if (database == TempoAtomStore.this.uniqueIndices) {
				for (LedgerIndex index : tempoAtomIndices.getUniqueIndices()) {
					secondaries.add(new DatabaseEntry(index.getKey()));
				}
			}

			if (database == TempoAtomStore.this.duplicatedIndices) {
				for (LedgerIndex index : tempoAtomIndices.getDuplicateIndices()) {
					secondaries.add(new DatabaseEntry(index.getKey()));
				}
			}
		}
	}

	private static class AtomStoreViewAdapter implements AtomStoreView {
		private final TempoAtomStore store;

		private AtomStoreViewAdapter(TempoAtomStore store) {
			this.store = Objects.requireNonNull(store, "store is required");
		}

		@Override
		public boolean contains(AID aid) {
			return store.contains(aid);
		}

		@Override
		public Optional<TempoAtom> get(AID aid) {
			return store.get(aid);
		}

		@Override
		public LedgerCursor search(Type type, LedgerIndex index, LedgerSearchMode mode) {
			return store.search(type, index, mode);
		}

		@Override
		public Pair<ImmutableList<AID>, IterativeCursor> getNext(IterativeCursor cursor, int limit, ShardSpace shardSpace) {
			return store.getNext(cursor, limit, shardSpace);
		}
	}
}
