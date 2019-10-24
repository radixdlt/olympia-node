package org.radix.shards;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.radix.atoms.events.AtomEvent;
import org.radix.atoms.events.AtomListener;
import org.radix.atoms.events.AtomStoredEvent;
import org.radix.database.DatabaseEnvironment;
import org.radix.database.DatabaseStore;
import org.radix.database.exceptions.DatabaseException;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;
import org.radix.modules.exceptions.ModuleResetException;
import org.radix.modules.exceptions.ModuleStartException;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.SystemMetaData;
import org.radix.utils.SystemProfiler;

import org.radix.atoms.Atom;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Longs;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

/**
 * Basic store with integrated backing cache for performant checksum updates to the ledger.
 *
 * TODO perhaps needs a better flushing mechanism that is in a dedicated thread rather than a submitted execution.
 *
 * @author Dan
 *
 */
public class ShardChecksumStore extends DatabaseStore
{
	private static final Logger atomsLog = Logging.getLogger("atoms");

	private Database checksumsDatabase;
	private final transient AtomicLong	checksum = new AtomicLong(0);

	public ShardChecksumStore()
	{
		super();
	}

	@Override
	public void start_impl() throws ModuleException
	{
		DatabaseConfig checksumsConfig = new DatabaseConfig();
		checksumsConfig.setAllowCreate(true);
		checksumsConfig.setTransactional(true);
		checksumsConfig.setKeyPrefixing(true);

		try
		{
			this.checksumsDatabase = Modules.get(DatabaseEnvironment.class).getEnvironment().openDatabase(null, "tempo.checksums", checksumsConfig);

			byte[] data = Modules.get(DatabaseEnvironment.class).get(getName(), "ledger.checksum");
			if (data != null)
			{
				this.checksum.set(Longs.fromByteArray(data));
				Modules.ifAvailable(SystemMetaData.class, a -> a.put("ledger.checksum", ShardChecksumStore.this.checksum.get()));
			}
		}
        catch (Exception ex)
        {
        	throw new ModuleStartException(ex, this);
		}

		Events.getInstance().register(AtomEvent.class, this.atomListener);

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
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "tempo.checksums", false);

			Modules.get(DatabaseEnvironment.class).put(transaction, getName(), "ledger.checksum", Longs.toByteArray(0l));
			Modules.get(DatabaseEnvironment.class).put(transaction, getName(), "recovery", Longs.toByteArray(0l));

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

		ShardChecksumStore.this.checksum.set(0l);
		Modules.ifAvailable(SystemMetaData.class, a -> a.put("ledger.checksum", ShardChecksumStore.this.checksum.get()));

		super.reset_impl();
	}

	@Override
	public void stop_impl() throws ModuleException
	{
		super.stop_impl();

		Events.getInstance().deregister(AtomEvent.class, this.atomListener);

		this.checksumsDatabase.close();
	}

	@Override
	public void build() throws DatabaseException { /* Not used */ }

	@Override
	public void maintenence() throws DatabaseException { /* Not used */ }

	@Override
	public void flush() throws DatabaseException
	{
	}

	@Override
	public void integrity() throws DatabaseException
	{
		// TODO reimplement this
	}

	public long getChecksum()
	{
		return this.checksum.get();
	}

	public long getChecksum(long shard) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		try
        {
			DatabaseEntry key = new DatabaseEntry(Longs.toByteArray(shard));
			DatabaseEntry data = new DatabaseEntry();

			if (this.checksumsDatabase.get(null, key, data, LockMode.DEFAULT) == OperationStatus.SUCCESS)
				return Longs.fromByteArray(data.getData());
		}
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("SHARD_CHECKSUM_STORE:GET_CHECKSUM", start);
		}

		return 0l;
	}

	public long getChecksum(int chunk, ShardRange range) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		long from = ShardSpace.fromChunk(chunk, ShardSpace.SHARD_CHUNK_HALF_RANGE) + range.getLow();
		long to = from + range.getSpan();
		long checksum = 0l;

		Cursor cursor = null;
		try
        {
			cursor = this.checksumsDatabase.openCursor(null, null);
			
			DatabaseEntry search = new DatabaseEntry(Longs.toByteArray(from));
			DatabaseEntry data = new DatabaseEntry();
			
			OperationStatus status = cursor.getSearchKeyRange(search, data, LockMode.DEFAULT);

			while (status.equals(OperationStatus.SUCCESS) == true)
			{
				if (Longs.fromByteArray(search.getData()) > to)
					break;
				
				checksum += Longs.fromByteArray(data.getData());
				
				status = cursor.getNext(search, data, LockMode.DEFAULT);
			}
		}
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			if (cursor != null)
				cursor.close();
			
			SystemProfiler.getInstance().incrementFrom("SHARD_CHECKSUM_STORE:GET_CHECKSUM:CHUNK:RANGE", start);
		}

		return checksum;
	}
	
	private final Queue<Atom> 	incrementQueue = new ConcurrentLinkedQueue<Atom>();
	private final Map<Long, Long> increments = new HashMap<Long, Long>();
	private final Lock			incrementLock = new ReentrantLock();

	private void incrementChecksums(Atom atom) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();
		
		Cursor cursor = null;
		Transaction transaction = null;

		this.incrementQueue.offer(atom);

		if (this.incrementLock.tryLock())
		{
			try
	        {
				long globalIncrement = 0;
				
				this.increments.clear();
				
				while((atom = this.incrementQueue.poll()) != null)
				{
					for (long shard : atom.getShards())
					{
						// TODO check this is ok, does it break anything for sync etc //
						if (LocalSystem.getInstance().getShards().intersects(shard) == false && Modules.get(Universe.class).getGenesis().contains(atom) == false)
							continue;
						
						long increment = this.increments.getOrDefault(shard, 0l);
						increment += atom.getHID().getLow();
						globalIncrement += atom.getHID().getLow();
						this.increments.put(shard, increment);
					}
				}
				
				transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, null);
				cursor = this.checksumsDatabase.openCursor(transaction, null);
	
				for (Entry<Long, Long> entry : this.increments.entrySet())
				{
					DatabaseEntry key = new DatabaseEntry(Longs.toByteArray(entry.getKey()));
					DatabaseEntry data = new DatabaseEntry(Longs.toByteArray(entry.getValue()));
					OperationStatus status = cursor.putNoOverwrite(key, data);
					
					if (status == OperationStatus.KEYEXIST)
					{
						status = cursor.getSearchKey(key, data, LockMode.RMW);
						
						if (status == OperationStatus.SUCCESS)
						{
							data.setData(Longs.toByteArray(Longs.fromByteArray(data.getData())+entry.getValue()));
							status = cursor.putCurrent(data);
						}
					}
					else if (status == OperationStatus.NOTFOUND)
					{
						data.setData(Longs.toByteArray(entry.getValue()));
						status = cursor.put(key, data);
					}
	
					if (status != OperationStatus.SUCCESS)
						throw new DatabaseException("Checksum increment for shard "+entry.getKey()+" failed due to "+status);
				}
				
				cursor.close();
				transaction.commit();
				
				this.checksum.addAndGet(globalIncrement);
			}
			catch (Exception ex)
			{
				cursor.close();
				transaction.abort();
				
				if (ex instanceof DatabaseException)
					throw ex;
				
				throw new DatabaseException(ex);
			}
			finally
			{
				if (cursor != null)
					cursor.close();
				
				this.incrementLock.unlock();
				
				SystemProfiler.getInstance().incrementFrom("SHARD_CHECKSUM_STORE:INCREMENT_CHECKSUMS", start);
			}
		}
	}

	private void decrementChecksums(Atom atom) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();
		
		Cursor cursor = null;
		Transaction transaction = null;

		try
        {
			long globalDecrement = 0;
			
			transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, null);
			cursor = this.checksumsDatabase.openCursor(transaction, null);

			for (long shard : atom.getShards())
			{
				// TODO check this is ok, does it break anything for sync etc //
				if (LocalSystem.getInstance().getShards().intersects(shard) == false && Modules.get(Universe.class).getGenesis().contains(atom) == false)
					continue;

				DatabaseEntry key = new DatabaseEntry(Longs.toByteArray(shard));
				DatabaseEntry data = new DatabaseEntry();
				OperationStatus status = cursor.getSearchKey(key, data, LockMode.RMW);
							
				if (status == OperationStatus.SUCCESS)
				{
					data.setData(Longs.toByteArray(Longs.fromByteArray(data.getData())-atom.getHID().getLow()));
					status = cursor.putCurrent(data);
				}
				else if (status == OperationStatus.NOTFOUND)
				{
					data.setData(Longs.toByteArray(-atom.getHID().getLow()));
					status = cursor.put(key, data);
				}

				if (status != OperationStatus.SUCCESS)
					throw new DatabaseException("Checksum decrement for Atom "+atom.getHID()+" failed due to "+status);
				
				globalDecrement -= atom.getHID().getLow();
			}
			
			cursor.close();
			transaction.commit();
			
			this.checksum.addAndGet(globalDecrement);
		}
		catch (Exception ex)
		{
			cursor.close();
			transaction.abort();
			
			if (ex instanceof DatabaseException)
				throw ex;
			
			throw new DatabaseException(ex);
		}
		finally
		{
			if (cursor != null)
				cursor.close();
			
			SystemProfiler.getInstance().incrementFrom("SHARD_CHECKSUM_STORE:DECREMENT_CHECKSUMS", start);
		}
	}

	public void dumpShardChunkChecksums() throws DatabaseException
	{
	}

	// ATOM LISTENER //
	private AtomListener atomListener = new AtomListener()
	{
		@Override
		public void process(AtomEvent event) throws Throwable
		{
			if (event instanceof AtomStoredEvent)
				ShardChecksumStore.this.incrementChecksums(event.getAtom());
	
			Modules.ifAvailable(SystemMetaData.class, a -> a.put("ledger.checksum", ShardChecksumStore.this.checksum.get()));
		}
	};
}
