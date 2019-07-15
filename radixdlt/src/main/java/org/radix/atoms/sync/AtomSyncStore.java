package org.radix.atoms.sync;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.util.concurrent.AtomicDouble;
import org.radix.atoms.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;

import org.radix.atoms.AtomDiscoveryRequest;
import org.radix.atoms.AtomStore;
import org.radix.atoms.PreparedAtom;
import org.radix.atoms.events.AtomDeletedEvent;
import org.radix.atoms.events.AtomEvent;
import org.radix.atoms.events.AtomListener;
import org.radix.atoms.events.AtomStoredEvent;
import org.radix.atoms.events.AtomUpdatedEvent;
import org.radix.common.executors.Executor;
import org.radix.database.DatabaseEnvironment;
import org.radix.database.DatabaseStore;
import org.radix.database.exceptions.DatabaseException;
import org.radix.discovery.DiscoveryCursor;
import org.radix.discovery.DiscoveryException;
import org.radix.discovery.DiscoveryRequest.Action;
import org.radix.discovery.DiscoverySource;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;
import org.radix.modules.exceptions.ModuleResetException;
import org.radix.modules.exceptions.ModuleStartException;
import org.radix.properties.RuntimeProperties;
import com.radixdlt.utils.WireIO.Reader;
import com.radixdlt.utils.WireIO.Writer;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.SystemMetaData;
import org.radix.utils.SystemProfiler;
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

public class AtomSyncStore extends DatabaseStore implements DiscoverySource<AtomDiscoveryRequest>
{
	private static final Logger atomsLog = Logging.getLogger("atoms");

	private static final long	ATOM_SYNC_BLOCK_SIZE = 256;

	class AtomSyncBlock implements Comparable<AtomSyncBlock>
	{
		class AtomSyncEntry
		{
			private AID ID;
			private final List<Long> shards = new ArrayList<>();

			public AtomSyncEntry(AID ID, Collection<Long> shards)
			{
				this.ID = ID;
				this.shards.addAll(shards);
			}

			public AtomSyncEntry(byte[] bytes) throws IOException
			{
				fromByteArray(bytes);
			}

			public AID getID()
			{
				return this.ID;
			}

			public List<Long> getShards()
			{
				return this.shards;
			}

			public byte[] toByteArray() throws IOException
			{
				try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
					Writer writer = new Writer(baos);
					writer.writeAID(this.ID);
					writer.writeInt(this.shards.size());

					for (long shard : this.shards)
						writer.writeLong(shard);

					return baos.toByteArray();
				}
			}

			public void fromByteArray(byte[] bytes) throws IOException
			{
				Reader reader = new Reader(bytes);

				this.ID = reader.readAID();
				int numShards = reader.readInt();
				for (int s = 0 ; s < numShards ; s++)
					this.shards.add(reader.readLong());
			}
		}

		private long clock;
		private volatile boolean modified;
		private final AtomicLong modifiedAt = new AtomicLong(0l);
		private final ReentrantLock lock = new ReentrantLock(true);
		private final Map<Long, AtomSyncEntry> atomSyncEntries = new HashMap<>();

		public AtomSyncBlock(byte[] bytes) throws IOException
		{
			fromByteArray(bytes);

			this.modified = false;
		}

		public AtomSyncBlock(long clock)
		{
			if (clock < 0)
				throw new IllegalArgumentException("Clock "+clock+" is less zero");

			this.clock = clock;
			this.modified = false;
		}

		@Override
		public int compareTo(AtomSyncBlock other)
		{
			if (this.clock < other.clock)
				return -1;

			if (this.clock > other.clock)
				return 1;

			return 0;
		}

		public long getClock()
		{
			return this.clock;
		}

		boolean isModified()
		{
			return this.modified;
		}

		void setModified(long clock)
		{
			this.modified = true;
			this.modifiedAt.compareAndSet(0, clock);
		}

		void setNotModified()
		{
			this.modified = false;
			this.modifiedAt.set(0l);
		}

		public AtomSyncEntry get(long clock)
		{
			if (clock < this.clock)
				throw new IllegalArgumentException("Clock "+clock+" is less than block start clock of "+this.clock);

			if (clock > this.clock+ATOM_SYNC_BLOCK_SIZE)
				throw new IllegalArgumentException("Clock "+clock+" is greater than allowed by block of "+(this.clock + ATOM_SYNC_BLOCK_SIZE));

			this.lock.lock();
			try
			{
				if (this.atomSyncEntries.containsKey(clock) == true)
					return this.atomSyncEntries.get(clock);

				return null;
			}
			finally
			{
				this.lock.unlock();
			}
		}

		public long last()
		{
			this.lock.lock();
			try
			{
				long last = 0;
				for (long clock : this.atomSyncEntries.keySet())
					if (clock > last)
						last = clock;

				return last;
			}
			finally
			{
				this.lock.unlock();
			}
		}

		public void put(long clock, PreparedAtom preparedAtom)
		{
			if (clock < this.clock)
				throw new IllegalArgumentException("Clock "+clock+" is less than block start clock of "+this.clock);

			if (clock > this.clock+ATOM_SYNC_BLOCK_SIZE)
				throw new IllegalArgumentException("Clock "+clock+" is greater than allowed by block of "+(this.clock + ATOM_SYNC_BLOCK_SIZE));

			this.lock.lock();
			try
			{
				if (this.atomSyncEntries.containsKey(clock) == true)
					throw new IllegalStateException("Clock slot "+clock+" already occupied");

				AtomSyncEntry atomSyncEntry = new AtomSyncEntry(preparedAtom.getAtomID(), preparedAtom.getShards());
				this.atomSyncEntries.put(clock, atomSyncEntry);
				setModified(clock);
			}
			finally
			{
				this.lock.unlock();
			}
		}

		public AtomSyncEntry replace(long clock, PreparedAtom preparedAtom)
		{
			if (clock < this.clock)
				throw new IllegalArgumentException("Clock "+clock+" is less than block start clock of "+this.clock);

			if (clock > this.clock+ATOM_SYNC_BLOCK_SIZE)
				throw new IllegalArgumentException("Clock "+clock+" is greater than allowed by block of "+(this.clock + ATOM_SYNC_BLOCK_SIZE));

			this.lock.lock();
			try
			{
				if (this.atomSyncEntries.containsKey(clock) == false)
					throw new IllegalStateException("Clock slot "+clock+" is not occupied");

				AtomSyncEntry atomSyncEntry = new AtomSyncEntry(preparedAtom.getAtomID(), preparedAtom.getShards());
				AtomSyncEntry replacedAtomSyncEntry = this.atomSyncEntries.put(clock, atomSyncEntry);
				setModified(clock);
				return replacedAtomSyncEntry;
			}
			finally
			{
				this.lock.unlock();
			}
		}

		public AtomSyncEntry remove(long clock)
		{
			if (clock < this.clock)
				throw new IllegalArgumentException("Clock "+clock+" is less than block start clock of "+this.clock);

			if (clock > this.clock+ATOM_SYNC_BLOCK_SIZE)
				throw new IllegalArgumentException("Clock "+clock+" is greater than allowed by block of "+(this.clock + ATOM_SYNC_BLOCK_SIZE));

			this.lock.lock();
			try
			{
				AtomSyncEntry atomSyncEntry = this.atomSyncEntries.remove(clock);
				setModified(clock);
				return atomSyncEntry;
			}
			finally
			{
				this.lock.unlock();
			}
		}

		@Override
		public String toString()
		{
			return this.clock + " Modified: "+this.modified+" Size: "+this.atomSyncEntries.size();
		}

		public byte[] toByteArray() throws IOException
		{
			this.lock.lock();
			try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
				Writer writer = new Writer(baos);
				writer.writeLong(this.clock);
				writer.writeInt(this.atomSyncEntries.size());
				for (Long clock : this.atomSyncEntries.keySet())
				{
					writer.writeLong(clock);
					writer.writeVarBytes(this.atomSyncEntries.get(clock).toByteArray());
				}

				return baos.toByteArray();
			} finally {
				this.lock.unlock();
			}
		}

		public void fromByteArray(byte[] bytes) throws IOException
		{
			Reader reader = new Reader(bytes);

			this.clock = reader.readLong();
			int numClocks = reader.readInt();
			for (int c = 0 ; c < numClocks ; c++)
			{
				long clock = reader.readLong();
				AtomSyncEntry atomSyncEntry = new AtomSyncEntry(reader.readVarBytes());
				this.atomSyncEntries.put(clock, atomSyncEntry);
			}
		}
	}

	private Database syncStateDatabase = null;
	private Database syncDatabase;
	private final Semaphore capacity;
	private final AtomicLong recovery = new AtomicLong(0l);
	private long flushTime;
	private final AtomicBoolean flushing = new AtomicBoolean(false);
	private final AtomicBoolean flushPending = new AtomicBoolean(false);
	private final Map<Long, AtomSyncBlock> blocks;
	private final transient AtomicLong stored = new AtomicLong(0);
	private final transient AtomicDouble storedPerShard = new AtomicDouble(0);

	public AtomSyncStore()
	{
		super();

		this.capacity = new Semaphore(Modules.get(RuntimeProperties.class).get("tempo.sync.cache.capacity", 256));
		this.blocks = new ConcurrentHashMap<>(capacity.availablePermits());
	}

	@Override
	public void start_impl() throws ModuleException
	{
		DatabaseConfig syncConfig = new DatabaseConfig();
		syncConfig.setAllowCreate(true);
		syncConfig.setTransactional(true);
		syncConfig.setKeyPrefixing(true);

		DatabaseConfig syncStateConfig = new DatabaseConfig();
		syncStateConfig.setAllowCreate(true);

		try
		{
			this.syncDatabase = Modules.get(DatabaseEnvironment.class).getEnvironment().openDatabase(null, "tempo.sync", syncConfig);
			this.syncStateDatabase = Modules.get(DatabaseEnvironment.class).getEnvironment().openDatabase(null, "tempo.sync_state", syncStateConfig);

			byte[] data = Modules.get(DatabaseEnvironment.class).get(getName(), "ledger.stored");
			if (data != null)
				this.stored.set(Longs.fromByteArray(data));

			data = Modules.get(DatabaseEnvironment.class).get(getName(), "ledger.storedPerShard");
			if (data != null)
				this.storedPerShard.set(ByteBuffer.wrap(data).getDouble());

			data = Modules.get(DatabaseEnvironment.class).get(getName(), "recovery");
			if (data != null)
				this.recovery.set(Longs.fromByteArray(data));
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
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "tempo.sync", false);
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "tempo.sync_state", false);

			Modules.get(DatabaseEnvironment.class).put(transaction, getName(), "ledger.stored", Longs.toByteArray(0l));
			Modules.get(DatabaseEnvironment.class).put(transaction, getName(), "ledger.storedPerShard", ByteBuffer.wrap(new byte[8]).putDouble(0).array());
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

		this.stored.set(0l);
		this.blocks.clear();

		super.reset_impl();
	}

	@Override
	public void stop_impl() throws ModuleException
	{
		super.stop_impl();

		Events.getInstance().deregister(AtomEvent.class, this.atomListener);

		this.syncStateDatabase.close();
		this.syncDatabase.close();
	}

	@Override
	public void build() throws DatabaseException { }

	@Override
	public void maintenence() throws DatabaseException { }

	@Override
	public void integrity() throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		try
		{
			long stored = 0;
			DatabaseEntry key = new DatabaseEntry();
			DatabaseEntry data = new DatabaseEntry();
			AtomSyncBlock atomSyncBlock = null;

			Atom lastAtom = Modules.get(AtomStore.class).getLastAtom();

			if (lastAtom != null)
			{
				for (long clock = this.recovery.get() ; clock <= lastAtom.getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID()).getClock() ; clock++)
				{
					PreparedAtom preparedAtom = Modules.get(AtomStore.class).getAtom(clock);

					long alignedClock = (clock / AtomSyncStore.ATOM_SYNC_BLOCK_SIZE) * AtomSyncStore.ATOM_SYNC_BLOCK_SIZE;
					key.setData(Longs.toByteArray(alignedClock));
					if (this.syncDatabase.get(null, key, data, LockMode.DEFAULT) == OperationStatus.SUCCESS)
						atomSyncBlock = new AtomSyncBlock(data.getData());
					else
						atomSyncBlock = new AtomSyncBlock(alignedClock);

					if (preparedAtom != null && atomSyncBlock.get(clock) == null)
						atomSyncBlock.put(clock, preparedAtom);
					else if (preparedAtom == null)
						atomSyncBlock.remove(clock);

					data.setData(atomSyncBlock.toByteArray());
					this.syncDatabase.put(null, key, data);
				}

				for (long clock = 0 ; clock < lastAtom.getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID()).getClock() ; clock += AtomSyncStore.ATOM_SYNC_BLOCK_SIZE)
				{
					key.setData(Longs.toByteArray(clock));
					if (this.syncDatabase.get(null, key, data, LockMode.DEFAULT) == OperationStatus.SUCCESS)
					{
						atomSyncBlock = new AtomSyncBlock(data.getData());
						stored += atomSyncBlock.atomSyncEntries.size();
					}
					else
						atomsLog.warn("AtomSyncBlock for clock "+clock+" not found");
				}

				this.stored.set(stored);
				Modules.ifAvailable(SystemMetaData.class, a -> a.put("ledger.stored", AtomSyncStore.this.stored.get()));
				Modules.ifAvailable(SystemMetaData.class, a -> a.put("ledger.storedPerShard", AtomSyncStore.this.storedPerShard.toString()));

			}
		}
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_SYNC_STORE:INTEGRITY", start);
		}
	}

	@Override
	public String getName() { return "Atom Sync Store"; }

	@Override
	public synchronized void flush()
	{
		if ((this.blocks.size() > this.capacity.availablePermits() || TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - this.flushTime) > 0) &&
			this.flushPending.compareAndSet(false, true) == true)
		{
			this.flushTime = System.currentTimeMillis();
			Executor.getInstance().submit(new Callable<AtomSyncStore>()
			{
				@Override
				public AtomSyncStore call() throws Exception
				{
					doFlush();
					return AtomSyncStore.this;
				}
			});
		}
	}

	private void doFlush() throws DatabaseException
	{
		if (this.flushing.compareAndSet(false, true) == false)
			return;

		if (AtomSyncStore.this.blocks.isEmpty() == false)
		{
			do
			{
				List<AtomSyncBlock> atomSyncBlocks = new ArrayList<>();
				AtomSyncBlock atomSyncBlock = null;

				AtomSyncStore.this.recovery.set(Long.MAX_VALUE);
				Iterator<AtomSyncBlock> atomSyncBlockIterator = AtomSyncStore.this.blocks.values().iterator();
				while (atomSyncBlockIterator.hasNext())
				{
					atomSyncBlock = atomSyncBlockIterator.next();

					if (atomSyncBlock.lock.tryLock() == true)
					{
						try
						{
							if (atomSyncBlock.isModified() == false)
							{
								atomSyncBlockIterator.remove();
								AtomSyncStore.this.capacity.release();
								continue;
							}

							atomSyncBlocks.add(atomSyncBlock);

							if (atomSyncBlock.modifiedAt.get() < AtomSyncStore.this.recovery.get())
								AtomSyncStore.this.recovery.set(atomSyncBlock.modifiedAt.get());
						}
						finally
						{
							atomSyncBlock.lock.unlock();
						}
					}
				}

				Collections.sort(atomSyncBlocks);

				Transaction transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, null);
				try
				{
					DatabaseEntry key = new DatabaseEntry();
					DatabaseEntry serialized = new DatabaseEntry();

					atomSyncBlockIterator = atomSyncBlocks.iterator();
					while (atomSyncBlockIterator.hasNext())
					{
						atomSyncBlock = atomSyncBlockIterator.next();

						atomSyncBlock.lock.lock();
						try
						{
							key.setData(Longs.toByteArray(atomSyncBlock.getClock()));
							serialized.setData(atomSyncBlock.toByteArray());
							OperationStatus status = AtomSyncStore.this.syncDatabase.put(transaction, key, serialized);
							atomSyncBlock.setNotModified();
							if (status != OperationStatus.SUCCESS)
								throw new IOException(status.name());
						}
						finally
						{
							atomSyncBlock.lock.unlock();
						}
					}

					Modules.get(DatabaseEnvironment.class).put(transaction, getName(), "ledger.stored", Longs.toByteArray(AtomSyncStore.this.stored.get()));
					Modules.get(DatabaseEnvironment.class).put(transaction, getName(), "ledger.storedPerShard", ByteBuffer.wrap(new byte[8]).putDouble(AtomSyncStore.this.storedPerShard.get()).array());
					Modules.get(DatabaseEnvironment.class).put(transaction, getName(), "recovery", Longs.toByteArray(AtomSyncStore.this.recovery.get()));

					transaction.commit();
				}
				catch(Exception ex)
				{
					transaction.abort();
					DatabaseException dbex = new DatabaseException("Failed to store AtomSyncBlock "+atomSyncBlock, ex);
					throw dbex;
				}
			}
			while (AtomSyncStore.this.blocks.size() > AtomSyncStore.this.capacity.availablePermits());

			this.flushing.set(false);
			this.flushPending.set(false);
		}
	}

	public AID getAtomID(long clock)
	{
		AtomSyncBlock.AtomSyncEntry atomSyncEntry = getAtomSyncEntry(clock);

		if (atomSyncEntry != null)
			return atomSyncEntry.getID();

		return null;
	}

	private AtomSyncBlock.AtomSyncEntry getAtomSyncEntry(long clock)
	{
		return this.apply(clock, atomSyncBlock -> atomSyncBlock.get(clock));
	}

	private void putAtom(long clock, PreparedAtom preparedAtom)
	{
		this.apply(clock, atomSyncBlock -> {
			atomSyncBlock.put(clock, preparedAtom);
			AtomSyncStore.this.stored.incrementAndGet();
			return null;
		});
	}

	private AtomSyncBlock.AtomSyncEntry removeAtom(long clock)
	{
		return this.apply(clock, atomSyncBlock -> {
			AtomSyncBlock.AtomSyncEntry removed = atomSyncBlock.remove(clock);
			AtomSyncStore.this.stored.decrementAndGet();
			return removed;
		});
	}

	private void replaceAtom(long clock, PreparedAtom preparedAtom)
	{
		this.apply(clock, atomSyncBlock -> { atomSyncBlock.replace(clock, preparedAtom); return null;} );
	}

	private <R> R apply(long clock, java.util.function.Function<AtomSyncBlock, R> function)
	{
		AtomicBoolean needAcquire = new AtomicBoolean(false);	// FIXME this is shitty!
		long alignedClock = (clock / AtomSyncStore.ATOM_SYNC_BLOCK_SIZE) * AtomSyncStore.ATOM_SYNC_BLOCK_SIZE;
		AtomSyncBlock atomSyncBlock = null;

		atomSyncBlock = this.blocks.computeIfAbsent(alignedClock, c -> {
				needAcquire.set(true);
				return new AtomSyncBlock(alignedClock);
		});

		atomSyncBlock.lock.lock();
		try
		{
			if (needAcquire.get() == true)
			{
				DatabaseEntry key = new DatabaseEntry(Longs.toByteArray(alignedClock));
				DatabaseEntry data = new DatabaseEntry();
				OperationStatus status = this.syncDatabase.get(null, key, data, LockMode.DEFAULT);

				if (status == OperationStatus.SUCCESS)
				{
					try
					{
						atomSyncBlock.fromByteArray(data.getData());
					}
					catch (IOException ioex)
					{
						atomsLog.error("Reading of AtomSyncBlock "+alignedClock+" for create failed", ioex);
					}
				}
			}

			R result = function.apply(atomSyncBlock);
			return result;
		}
		finally
		{
			atomSyncBlock.lock.unlock();

			flush();

			// TODO Figure out a nicer, more robust & more testable way of handling this acquire / free capacity logic
			// TODO Currently we can't be quite sure this actually works as intended since it's hard to test -Florian
			// TODO This kind of logic is duplicated across AtomSyncStore, NodeMassStore, ShardChecksumStore
			if (needAcquire.get() == true) {
				try {
					// We need to acquire capacity since we've put 1 new chunk in there.
					// We're doing this after actually putting the chunk to make synchronisation easier.
					if (!this.capacity.tryAcquire(1)) {
						// If we can't immediately acquire, we need to free some capacity.
						// By flushing a second time, all entries that previously weren't flushed are now flushed.
						doFlush();

						// Now we can try and acquire capacity again.
						if (!this.capacity.tryAcquire(1)) {
							// If there is still no capacity after flushing twice, we're in deep shit.
							atomsLog.fatal("Unable to acquire capacity after flushing");
						}
					}
				} catch (DatabaseException e) {
					log.error("Error while flushing to free capacity", e);
				}
			}
		}
	}

	public DiscoveryCursor getSyncState(EUID NID) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		try
        {
			DatabaseEntry 	key = new DatabaseEntry(NID.toByteArray());
			DatabaseEntry	value = new DatabaseEntry();

			OperationStatus status = this.syncStateDatabase.get(null, key, value, LockMode.DEFAULT);

			if (status == OperationStatus.SUCCESS)
				return Modules.get(Serialization.class).fromDson(value.getData(), DiscoveryCursor.class);
			else if (status == OperationStatus.NOTFOUND)
				return new DiscoveryCursor(0);
		}
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_SYNC_STORE:GET_SYNC_STATE", start);
		}

		return null;
	}

	public void storeSyncState(EUID NID, DiscoveryCursor cursor) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		try
        {
			this.syncStateDatabase.put(null, new DatabaseEntry(NID.toByteArray()), new DatabaseEntry(Modules.get(Serialization.class).toDson(cursor, Output.PERSIST)));
		}
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_SYNC_STORE:STORE_SYNC_STATE", start);
		}
	}

	public void dumpSyncBlocks() throws DatabaseException
	{
		long position = 1;
		long lastPosition = 0;

		DatabaseEntry key = new DatabaseEntry();
		try (Cursor cursor = this.syncDatabase.openCursor(null, null)) {
			if (cursor.getLast(key, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
				lastPosition = Longs.fromByteArray(key.getData())+AtomSyncStore.ATOM_SYNC_BLOCK_SIZE;
		}

		File syncBlockDumpFile = new File(Modules.get(RuntimeProperties.class).get("db.location", ".//RADIXDB")+"//sync_blocks_"+System.currentTimeMillis()+".dump");
		try (PrintStream syncBlockDumpStream = new PrintStream(syncBlockDumpFile)) {
			while (position < lastPosition)
			{
				AtomSyncBlock.AtomSyncEntry atomSyncEntry = getAtomSyncEntry(position);

				position++;

				if (atomSyncEntry != null)
					syncBlockDumpStream.println(position+": "+atomSyncEntry.ID);
			}
		}
		catch (Exception ex)
		{
			throw new DatabaseException("Could not dump Atoms", ex);
		}
	}

	// DISCOVERY //
	@Override
	public void discovery(AtomDiscoveryRequest request) throws DiscoveryException
	{
		if (request.getAction().equals(Action.DISCOVER) || request.getAction().equals(Action.DISCOVER_AND_DELIVER))
			query(request);

		if (request.getAction().equals(Action.DELIVER) || request.getAction().equals(Action.DISCOVER_AND_DELIVER))
			fetch(request);
	}

	@Override
	public void query(AtomDiscoveryRequest request) throws DiscoveryException
	{
		long start = SystemProfiler.getInstance().begin();

		List<AID> results = new ArrayList<>();

		try
		{
			DatabaseEntry key = new DatabaseEntry();
			DatabaseEntry data = new DatabaseEntry();

			long position = request.getCursor().getPosition();
			long nextPosition = request.getCursor().getPosition();
			long lastPosition = 0;

			try (Cursor cursor = this.syncDatabase.openCursor(null, null)) {
				if (cursor.getLast(key, data, LockMode.DEFAULT) == OperationStatus.SUCCESS)
					lastPosition = new AtomSyncBlock(data.getData()).last();
			}

			for (AtomSyncBlock atomSyncBlock : this.blocks.values())
				if (atomSyncBlock.last() > lastPosition)
					lastPosition = atomSyncBlock.last();

			while (position <= lastPosition)
			{
				AtomSyncBlock.AtomSyncEntry atomSyncEntry = getAtomSyncEntry(position);

				position++;

				if (atomSyncEntry != null)
				{
					nextPosition = position;

					if (request.getShards().intersects(atomSyncEntry.getShards()) == true)
					{
						if (Modules.get(AtomStore.class).hasAtom(atomSyncEntry.getID()) == true)
						{
							results.add(atomSyncEntry.getID());
							if (results.size() >= request.getLimit())
								break;
						}
					}
				}
			}

			if (nextPosition != request.getCursor().getPosition())
				request.getCursor().setNext(new DiscoveryCursor(position));
		}
		catch (IOException ioex)
		{
			throw new DiscoveryException(ioex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_DISCOVERY:SYNC", start);
		}

		request.setInventory(results);
	}

	@Override
	public void fetch(AtomDiscoveryRequest request) throws DiscoveryException
	{
		long start = SystemProfiler.getInstance().begin();

		try
		{
			request.setDelivered(Modules.get(AtomStore.class).getAtoms(request.getInventory()));
		}
		catch (IOException ioex)
		{
			throw new DiscoveryException(ioex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:FETCH", start);
		}
	}

	// ATOM LISTENER //
	private AtomListener atomListener = new AtomListener()
	{
		@Override
		public void process(AtomEvent event)
		{
			if (event instanceof AtomStoredEvent)
				AtomSyncStore.this.putAtom(event.getAtom().getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID()).getClock(), ((AtomStoredEvent) event).getPreparedAtom());

			if (event instanceof AtomUpdatedEvent)
				AtomSyncStore.this.replaceAtom(event.getAtom().getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID()).getClock(), ((AtomUpdatedEvent) event).getPreparedAtom());

			if (event instanceof AtomDeletedEvent)
				AtomSyncStore.this.removeAtom(event.getAtom().getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID()).getClock());

			Modules.ifAvailable(SystemMetaData.class, a -> a.put("ledger.stored", AtomSyncStore.this.stored.get()));
			Modules.ifAvailable(SystemMetaData.class, a -> a.put("ledger.storedPerShard", AtomSyncStore.this.storedPerShard.toString()));
		}
	};
}