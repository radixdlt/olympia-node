package com.radixdlt.tempo.store;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bouncycastle.util.Arrays;
import org.radix.database.DatabaseEnvironment;
import org.radix.database.DatabaseStore;
import org.radix.database.exceptions.DatabaseException;
import org.radix.discovery.DiscoveryCursor;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;
import org.radix.modules.exceptions.ModuleResetException;
import org.radix.modules.exceptions.ModuleStartException;
import org.radix.shards.ShardRange;
import org.radix.shards.ShardSpace;
import org.radix.time.TemporalVertex;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.SystemProfiler;

import com.google.common.collect.Lists;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerCursor.Type;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.tempo.AtomStore;
import com.radixdlt.tempo.AtomStoreView;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoCursor;
import com.radixdlt.utils.Longs;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.SecondaryMultiKeyCreator;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

public class TempoAtomStore extends DatabaseStore implements AtomStore
{
	private static final Logger atomsLog = Logging.getLogger ("atoms");

	public static class AtomStorePackedPrimaryKeyComparator implements Comparator<byte[]>
	{
		@Override
		public int compare(byte[] primary1, byte[] primary2)
		{
			for (int b = 0 ; b < Long.BYTES ; b++) {
				if (primary1[b] < primary2[b])
					return -1;
				else if (primary1[b] > primary2[b])
					return 1;
			}

			return 0;
		}
	}
	
	public final static byte ATOM_INDEX_PREFIX = 0;
	public final static byte SHARD_INDEX_PREFIX = 1;
	public final static byte DESTINATION_INDEX_PREFIX = 2;

	private final class TempoAtomIndexables
	{
		private final Set<LedgerIndex> uniqueIndices;
		private final Set<LedgerIndex> duplicateIndices;
		
		public TempoAtomIndexables(TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices)
		{
			for (LedgerIndex uniqueIndex : uniqueIndices)
				if (uniqueIndex.getPrefix() == ATOM_INDEX_PREFIX ||
					uniqueIndex.getPrefix() == SHARD_INDEX_PREFIX ||
					uniqueIndex.getPrefix() == DESTINATION_INDEX_PREFIX)
					throw new IllegalArgumentException("Unique indices for "+atom.getAID()+" contains Tempo restricted prefixes");

			for (LedgerIndex duplicateIndex : duplicateIndices)
				if (duplicateIndex.getPrefix() == ATOM_INDEX_PREFIX ||
					duplicateIndex.getPrefix() == SHARD_INDEX_PREFIX ||
					duplicateIndex.getPrefix() == DESTINATION_INDEX_PREFIX)
					throw new IllegalArgumentException("Duplicate indices for "+atom.getAID()+" contains Tempo restricted prefixes");

			this.uniqueIndices = new HashSet<>(uniqueIndices);
			this.uniqueIndices.add(new LedgerIndex(ATOM_INDEX_PREFIX, atom.getAID().getBytes()));
			
			this.duplicateIndices = new HashSet<>(duplicateIndices);
			for (Long shard : atom.getShards())
				this.duplicateIndices.add(new LedgerIndex(SHARD_INDEX_PREFIX, Longs.toByteArray(shard)));
		}

		public Set<LedgerIndex> getUniqueIndices()
		{
			return this.uniqueIndices;
		}

		public Set<LedgerIndex> getDuplicateIndices()
		{
			return this.duplicateIndices;
		}
	}

	private Database			atomsDatabase;
	private SecondaryDatabase	uniqueIndexables;
	private SecondaryDatabase	duplicatedIndexables;
	private final Map<Long, TempoAtomIndexables> processing = new ConcurrentHashMap<Long, TempoAtomIndexables>();

	private class AtomMultipleSecondaryKeyCreator implements SecondaryMultiKeyCreator
	{
		@Override
		public void createSecondaryKeys(SecondaryDatabase database, DatabaseEntry key, DatabaseEntry value, Set<DatabaseEntry> secondaries)
		{
			// key should be primary key where first 8 bytes is the long clock
			TempoAtomIndexables tempoAtomIndexables = TempoAtomStore.this.processing.get(Longs.fromByteArray(key.getData()));

			if (tempoAtomIndexables == null)
				throw new IllegalStateException("Indexables for Atom @ "+Longs.fromByteArray(key.getData())+" not in processing map");

			if (database == TempoAtomStore.this.uniqueIndexables)
			{
				for (LedgerIndex indexable : tempoAtomIndexables.getUniqueIndices())
					secondaries.add(new DatabaseEntry(indexable.getKey()));
			}

			if (database == TempoAtomStore.this.duplicatedIndexables)
			{
				for (LedgerIndex indexable : tempoAtomIndexables.getDuplicateIndices())
					secondaries.add(new DatabaseEntry(indexable.getKey()));
			}
		}
	}

	public TempoAtomStore()
	{
		super();
	}

	@Override
	public void start_impl() throws ModuleException
	{
		DatabaseConfig primaryConfig = new DatabaseConfig();
		primaryConfig.setAllowCreate(true);
		primaryConfig.setTransactional(true);
		primaryConfig.setKeyPrefixing(true);
		primaryConfig.setBtreeComparator(TempoAtomStore.AtomStorePackedPrimaryKeyComparator.class);

		SecondaryConfig uniqueIndexablesConfig = new SecondaryConfig();
		uniqueIndexablesConfig.setAllowCreate(true);
		uniqueIndexablesConfig.setTransactional(true);
		uniqueIndexablesConfig.setMultiKeyCreator(new AtomMultipleSecondaryKeyCreator());

		SecondaryConfig duplicatedIndexablesConfig = new SecondaryConfig();
		duplicatedIndexablesConfig.setAllowCreate(true);
		duplicatedIndexablesConfig.setTransactional(true);
		duplicatedIndexablesConfig.setSortedDuplicates(true);
		duplicatedIndexablesConfig.setMultiKeyCreator(new AtomMultipleSecondaryKeyCreator());

		try
		{
			this.atomsDatabase = Modules.get(DatabaseEnvironment.class).getEnvironment().openDatabase(null, "tempo2.atoms", primaryConfig);
			this.uniqueIndexables = Modules.get(DatabaseEnvironment.class).getEnvironment().openSecondaryDatabase(null, "tempo2.unique_indexables", this.atomsDatabase, uniqueIndexablesConfig);
			this.duplicatedIndexables = Modules.get(DatabaseEnvironment.class).getEnvironment().openSecondaryDatabase(null, "tempo2.duplicated_indexables", this.atomsDatabase, duplicatedIndexablesConfig);
		}
        catch (Exception ex)
        {
        	throw new ModuleStartException(ex, this);
		}

		super.start_impl();
	}

	@Override
	public void reset_impl() throws ModuleException
	{
		Transaction transaction = null;

		try
		{
			Modules.get(DatabaseEnvironment.class).lock();

			transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "tempo2.atoms", false);
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "tempo2.unique_indexables", false);
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "tempo2.duplicated_indexables", false);

			transaction.commit();
		}
		catch (DatabaseNotFoundException dsnfex)
		{
			if (transaction != null)
				transaction.abort();

			log.warn(dsnfex.getMessage());
		}
		catch (Exception ex)
		{
			if (transaction != null)
				transaction.abort();

			throw new ModuleResetException(ex, this);
		}
		finally
		{
			Modules.get(DatabaseEnvironment.class).unlock();
		}

		super.reset_impl();
	}

	@Override
	public void stop_impl() throws ModuleException
	{
		super.stop_impl();

		if (this.uniqueIndexables != null) this.uniqueIndexables.close();
		if (this.duplicatedIndexables != null) this.duplicatedIndexables.close();
		if (this.atomsDatabase != null) this.atomsDatabase.close();
	}

	@Override
	public void build() throws DatabaseException { /* Not used */ }

	@Override
	public void maintenence() throws DatabaseException { /* Not used */ }

	@Override
	public void integrity() throws DatabaseException
	{
		// TODO implement at a later date.  Also require mechanism for LocalSystem to recover local state from ledger
	}

	@Override
	public void flush() throws DatabaseException { /* Not used */ }

	@Override
	public String getName()
	{
		return "Atom Store";
	}

	public boolean contains(long clock)
	{
		long start = SystemProfiler.getInstance().begin();

		try
        {
			DatabaseEntry key = new DatabaseEntry(Longs.toByteArray(clock));
			if (this.atomsDatabase.get(null, key, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
				return true;
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:CONTAINS:CLOCK", start);
		}

		return false;
	}

	@Override
	public boolean contains(AID aid)
	{
		long start = SystemProfiler.getInstance().begin();

		try
        {
			DatabaseEntry key = new DatabaseEntry(LedgerIndex.from(Byte.MAX_VALUE, aid.getBytes()));
			if (OperationStatus.SUCCESS == this.uniqueIndexables.get(null, key, null, LockMode.DEFAULT))
				return true;

			return false;
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:CONTAINS:CLOCK", start);
		}
	}

	public Optional<TempoAtom> get(long clock)
	{
		long start = SystemProfiler.getInstance().begin();

		try
        {
			DatabaseEntry key = new DatabaseEntry(Longs.toByteArray(clock));
			DatabaseEntry value = new DatabaseEntry();

			if (this.atomsDatabase.get(null, key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS)
				return Optional.of(Serialization.getDefault().fromDson(value.getData(), TempoAtom.class));
		}
		catch (Throwable t)
		{
			atomsLog.error("Get of TempoAtom with clock "+clock+" failed", t);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:GET:CLOCK", start);
		}

		return Optional.empty();
	}

	@Override
	public Optional<TempoAtom> get(AID aid)
	{
		long start = SystemProfiler.getInstance().begin();

		try
        {
			DatabaseEntry key = new DatabaseEntry(LedgerIndex.from(Byte.MAX_VALUE, aid.getBytes()));
			DatabaseEntry value = new DatabaseEntry();

			if (this.uniqueIndexables.get(null, key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS)
				return Optional.of(Serialization.getDefault().fromDson(value.getData(), TempoAtom.class));
		}
		catch (Throwable t)
		{
			atomsLog.error("Get of TempoAtom with AID "+aid+" failed", t);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:GET:AID", start);
		}

		return Optional.empty();
	}

	@Override
	public boolean delete(AID aid)
	{
		long start = SystemProfiler.getInstance().begin();

		DatabaseEntry pKey = new DatabaseEntry();
		DatabaseEntry key = new DatabaseEntry(LedgerIndex.from(Byte.MAX_VALUE, aid.getBytes()));
		Transaction transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, null);

		try
        {
			if (this.uniqueIndexables.get(transaction, key, pKey, null, LockMode.RMW) == OperationStatus.SUCCESS)
			{
				if (this.atomsDatabase.delete(transaction, pKey) == OperationStatus.SUCCESS)
				{
					transaction.commit();
					return true;
				}
			}

			transaction.abort();
		}
		catch (Throwable t)
		{
			atomsLog.error("Delete of TempoAtom with AID "+aid+" failed", t);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:DELETE_ATOM", start);
		}

		return false;
	}

	@Override
	public synchronized boolean store(TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices)
	{
		long start = SystemProfiler.getInstance().begin();
		
		TemporalVertex localTemporalVertex = atom.getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID());
		if (localTemporalVertex == null)
			return false;

		DatabaseEntry pKey = null;
		DatabaseEntry data = null;
		Transaction transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, null);

		try
        {
			OperationStatus status;
			pKey = new DatabaseEntry(Arrays.concatenate(Longs.toByteArray(localTemporalVertex.getClock()), atom.getAID().getBytes()));
			data = new DatabaseEntry(Serialization.getDefault().toDson(atom, Output.PERSIST));
			
			this.processing.put(localTemporalVertex.getClock(), new TempoAtomIndexables(atom, uniqueIndices, duplicateIndices));

			status = this.atomsDatabase.putNoOverwrite(transaction, pKey, data);
			if (status.equals(OperationStatus.SUCCESS) == false)
				return false;

/*			{
				if (status.equals(OperationStatus.KEYEXIST) == true)
					throw new DatabaseException(this.atomsDatabase.getDatabaseName(), pKey);
				else
					throw new DatabaseException("Failed to store Atom "+atom+" due to "+status.name());
			}*/

			transaction.commit();
			return true;
		}
		catch (Throwable t)
		{
			transaction.abort();

			atomsLog.error("Store of TempoAtom "+atom+" failed", t);
			
			// FIXME need to handle this, but the exception handling sucks, and its LATE!
//			if (t instanceof UniqueConstraintException)
//				throw t;
			
			return false;
		}
		finally
		{
			if (data != null && data.getData() != null && data.getData().length > 0)
				this.processing.remove(localTemporalVertex.getClock());

			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:STORE", start);
		}
	}

	@Override
	// TODO make this properly Atomic
	public boolean replace(Set<AID> aids, TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices)
	{
		for (AID aid : aids) {
			this.delete(aid);
		}

		return this.store(atom, uniqueIndices, duplicateIndices);
	}

	@Override
	public AtomStoreView asReadOnlyView()
	{
		// TODO Auto-generated method stub
		return null;
	}

	
	// SHARDS //
	// TODO we'll need some way to collect the AID -> shard information from the AtomStore
	// leaving in place for now, stubbed, will review later when implementing ShardChecksumSync
	public Set<AID> getByShardChunkAndRange(int chunk, ShardRange range) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		try
        {
			long from = ShardSpace.fromChunk(chunk, ShardSpace.SHARD_CHUNK_HALF_RANGE) + range.getLow();
			long to = from + range.getSpan();

			return this.getByShardRange(from, to);
        }
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:GET_BY_SHARD_CHUNK_AND_RANGE", start);
		}
	}

	public Set<AID> getByShardRange(long from, long to) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		SecondaryCursor cursor = null;
		Set<AID> atomIds = new HashSet<>();

		try
        {
			cursor = this.duplicatedIndexables.openCursor(null, null);

			DatabaseEntry key = new DatabaseEntry(LedgerIndex.from(SHARD_INDEX_PREFIX, Longs.toByteArray(from)));
			DatabaseEntry value = new DatabaseEntry();

			// FIXME this is slow as shit, speed it up, maybe pack clock+HID for primary and use that
			OperationStatus status = cursor.getSearchKeyRange(key, value, LockMode.DEFAULT);
			while (status == OperationStatus.SUCCESS)
			{
				long shard = Longs.fromByteArray(key.getData(), 1);
				if (shard < from || shard > to)
					break;

				TempoAtom atom = Serialization.getDefault().fromDson(value.getData(), TempoAtom.class);
				atomIds.add(atom.getAID());

				status = cursor.getNextDup(key, value, LockMode.DEFAULT);
				if (status == OperationStatus.NOTFOUND)
					status = cursor.getNext(key, value, LockMode.DEFAULT);
			}

			return atomIds;
        }
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			if (cursor != null)
				cursor.close();

			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:GET_BY_SHARD_RANGE", start);
		}
	}

	public Set<AID> getByShard(long shard) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		SecondaryCursor cursor = null;
		Set<AID> atomIds = new HashSet<>();

		try
        {
			cursor = this.duplicatedIndexables.openCursor(null, null);

			DatabaseEntry key = new DatabaseEntry(LedgerIndex.from(SHARD_INDEX_PREFIX, Longs.toByteArray(shard)));
			DatabaseEntry value = new DatabaseEntry();

			// FIXME this is slow as shit, speed it up, maybe pack clock+HID for primary and use that
			OperationStatus status = cursor.getSearchKey(key, value, LockMode.DEFAULT);
			while (status == OperationStatus.SUCCESS)
			{
				TempoAtom atom = Serialization.getDefault().fromDson(value.getData(), TempoAtom.class);
				atomIds.add(atom.getAID());
				status = cursor.getNextDup(key, value, LockMode.DEFAULT);
			}

			return atomIds;
        }
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			if (cursor != null)
				cursor.close();

			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:GET_BY_SHARD", start);
		}
	}

	/**
	 * Updates an Atom in the AtomStore.
	 * <br><br>
	 * In practice only the Atom meta data is updated, the Atom itself should be unmodified.
	 *
	 * @param atom
	 * @return
	 */
	public boolean update(TempoAtom atom)
	{
		// FIXME I think we'll need this if we want to update the TemporalProof stored with an Atom after a conflict resolution
		
		return false;
	}

	// FIXME DPH: shitty LC sync function that you'll need for iterative sync
	public List<AID> doAtomClockDiscovery(DiscoveryCursor syncCursor, int limit, ShardRange shardRange) throws DatabaseException
	{
		List<AID> aids = Lists.newArrayList();
		long start = SystemProfiler.getInstance().begin();
		
		try (Cursor cursor = this.atomsDatabase.openCursor(null, null)) {
			long position = syncCursor.getPosition();

			DatabaseEntry search = new DatabaseEntry(Longs.toByteArray(position + 1));
			DatabaseEntry value = new DatabaseEntry();
			OperationStatus status = cursor.getSearchKeyRange(search, value, LockMode.DEFAULT);

			while (status == OperationStatus.SUCCESS) 
			{
				TempoAtom atom = Serialization.getDefault().fromDson(value.getData(), TempoAtom.class);
				
				if (shardRange.intersects(atom.getShards()) == true)
				{
					position = Longs.fromByteArray(search.getData());
					aids.add(atom.getAID());

					if (aids.size() >= limit)
						break;
				}
				
				status = cursor.getNext(search, value, LockMode.DEFAULT);
			}

			if (position != syncCursor.getPosition()) {
				syncCursor.setNext(new DiscoveryCursor(position));
			}
		} catch (Throwable t) {
			throw new DatabaseException(t);
		} finally {
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:DISCOVER:SYNC", start);
		}

		return aids;
	}

	// LEDGER CURSOR HANDLING //
	public LedgerCursor search(Type type, LedgerIndex indexable, LedgerSearchMode mode)
	{
		Objects.requireNonNull(indexable);
		
		SecondaryCursor databaseCursor;
		
		if (type.equals(Type.UNIQUE) == true)
			databaseCursor = this.uniqueIndexables.openCursor(null, null);
		else if (type.equals(Type.DUPLICATE) == true)
			databaseCursor = this.duplicatedIndexables.openCursor(null, null);
		else
			throw new IllegalStateException("Type "+type+" not supported");
			
		try
		{
			DatabaseEntry pKey = new DatabaseEntry();
			DatabaseEntry key = new DatabaseEntry(indexable.getKey());
			
			if (mode == LedgerSearchMode.EXACT)
			{
				if (databaseCursor.getSearchKey(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
					return new TempoCursor(type, pKey.getData(), key.getData());
			}
			else if (mode == LedgerSearchMode.RANGE)
			{
				if (databaseCursor.getSearchKeyRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
					return new TempoCursor(type, pKey.getData(), key.getData());
			}
			
			return null;
		}
		finally
		{
			databaseCursor.close();
		}
	}
	
	public TempoCursor getNext(TempoCursor cursor) throws DatabaseException
	{
		Objects.requireNonNull(cursor);
		
		SecondaryCursor databaseCursor;
		
		if (cursor.getType().equals(Type.UNIQUE) == true)
			databaseCursor = this.uniqueIndexables.openCursor(null, null);
		else if (cursor.getType().equals(Type.DUPLICATE) == true)
			databaseCursor = this.duplicatedIndexables.openCursor(null, null);
		else
			throw new IllegalStateException("Type "+cursor.getType()+" not supported");
			
		try
		{
			DatabaseEntry pKey = new DatabaseEntry(cursor.getPrimary());
			DatabaseEntry key = new DatabaseEntry(cursor.getIndexable());
			
			if (databaseCursor.getSearchBothRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
			{
				if (databaseCursor.getNextDup(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
					return new TempoCursor(cursor.getType(), pKey.getData(), key.getData());
			}
			
			return null;
		}
		catch(Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			databaseCursor.close();
		}
	}
	
	public TempoCursor getPrev(TempoCursor cursor) throws DatabaseException
	{
		Objects.requireNonNull(cursor);
		
		SecondaryCursor databaseCursor;
		
		if (cursor.getType().equals(Type.UNIQUE) == true)
			databaseCursor = this.uniqueIndexables.openCursor(null, null);
		else if (cursor.getType().equals(Type.DUPLICATE) == true)
			databaseCursor = this.duplicatedIndexables.openCursor(null, null);
		else
			throw new IllegalStateException("Type "+cursor.getType()+" not supported");
			
		try
		{
			DatabaseEntry pKey = new DatabaseEntry(cursor.getPrimary());
			DatabaseEntry key = new DatabaseEntry(cursor.getIndexable());
			
			if (databaseCursor.getSearchBothRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
			{
				if (databaseCursor.getPrevDup(pKey, key, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
					return new TempoCursor(cursor.getType(), pKey.getData(), key.getData());
			}
			
			return null;
		}
		catch(Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			databaseCursor.close();
		}
	}
	
	public TempoCursor getFirst(TempoCursor cursor) throws DatabaseException
	{
		Objects.requireNonNull(cursor);
		
		SecondaryCursor databaseCursor;
		
		if (cursor.getType().equals(Type.UNIQUE) == true)
			databaseCursor = this.uniqueIndexables.openCursor(null, null);
		else if (cursor.getType().equals(Type.DUPLICATE) == true)
			databaseCursor = this.duplicatedIndexables.openCursor(null, null);
		else
			throw new IllegalStateException("Type "+cursor.getType()+" not supported");
			
		try
		{
			DatabaseEntry pKey = new DatabaseEntry(cursor.getPrimary());
			DatabaseEntry key = new DatabaseEntry(cursor.getIndexable());
			
			if (databaseCursor.getSearchBothRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
			{
				if (databaseCursor.getPrevNoDup(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
				{
					if (databaseCursor.getNext(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
						return new TempoCursor(cursor.getType(), pKey.getData(), key.getData());
				}
				else if (databaseCursor.getFirst(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
					return new TempoCursor(cursor.getType(), pKey.getData(), key.getData());
			}
			
			return null;
		}
		catch(Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			databaseCursor.close();
		}
	}

	public TempoCursor getLast(TempoCursor cursor) throws DatabaseException
	{
		Objects.requireNonNull(cursor);
		
		SecondaryCursor databaseCursor;
		
		if (cursor.getType().equals(Type.UNIQUE) == true)
			databaseCursor = this.uniqueIndexables.openCursor(null, null);
		else if (cursor.getType().equals(Type.DUPLICATE) == true)
			databaseCursor = this.duplicatedIndexables.openCursor(null, null);
		else
			throw new IllegalStateException("Type "+cursor.getType()+" not supported");
			
		try
		{
			DatabaseEntry pKey = new DatabaseEntry(cursor.getPrimary());
			DatabaseEntry key = new DatabaseEntry(cursor.getIndexable());
			
			if (databaseCursor.getSearchBothRange(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
			{
				if (databaseCursor.getNextNoDup(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
				{
					if (databaseCursor.getPrev(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
						return new TempoCursor(cursor.getType(), pKey.getData(), key.getData());
				}
				else if (databaseCursor.getLast(key, pKey, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
					return new TempoCursor(cursor.getType(), pKey.getData(), key.getData());
			}
			
			return null;
		}
		catch(Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			databaseCursor.close();
		}
	}
}
