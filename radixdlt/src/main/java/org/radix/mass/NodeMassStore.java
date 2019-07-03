package org.radix.mass;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.radix.atoms.PreparedAtom;
import org.radix.atoms.events.AtomDeletedEvent;
import org.radix.atoms.events.AtomEvent;
import org.radix.atoms.events.AtomListener;
import org.radix.atoms.events.AtomStoredEvent;
import org.radix.atoms.events.AtomUpdatedEvent;
import com.radixdlt.common.EUID;
import com.radixdlt.utils.Offset;
import org.radix.common.executors.Executor;
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
import org.radix.properties.RuntimeProperties;
import com.radixdlt.utils.WireIO.Reader;
import com.radixdlt.utils.WireIO.Writer;
import com.radixdlt.universe.Universe;
import org.radix.universe.system.LocalSystem;
import com.radixdlt.atoms.Atom;
import com.radixdlt.utils.UInt384;
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

public class NodeMassStore extends DatabaseStore
{
	private static final Logger atomsLog = Logging.getLogger("atoms");

	private class NodeMassEntry implements Comparable<NodeMassEntry>
	{
		private EUID NID;
		private volatile boolean partial;
		private volatile boolean modified;
		private final AtomicLong modifiedAt = new AtomicLong(0l);
		private final ReentrantLock lock = new ReentrantLock(true);
		private final Map<Integer, UInt384> plancks = new HashMap<>();

		public NodeMassEntry(byte[] bytes) throws IOException
		{
			fromByteArray(bytes);

			this.modified = false;
			this.partial = false;
		}

		public NodeMassEntry(EUID NID)
		{
			if (NID == null || NID.equals(EUID.ZERO) == true)
				throw new IllegalArgumentException("NID is null or zero");

			this.NID = NID;
			this.modified = false;
			this.partial = true;
		}

		@Override
		public int compareTo(NodeMassEntry other)
		{
			return this.NID.compareTo(other.NID);
		}

		public EUID getNID()
		{
			return this.NID;
		}

		public boolean isPartial()
		{
			return this.partial;
		}

		void setPartial(boolean partial)
		{
			this.partial = partial;
		}

		boolean isModified()
		{
			return this.modified;
		}

		void setModified(long clock)
		{
			this.modified = true;

			if (this.modifiedAt.get() == 0)
				this.modifiedAt.set(clock);
		}

		void setNotModified()
		{
			this.modified = false;
			this.modifiedAt.set(0l);
		}

		public void merge(NodeMassEntry other)
		{
			this.lock.lock();
			try
			{
				if (this.partial == false)
					throw new IllegalStateException("NodeMassEntry has already been merged / is not partial");

				if (other.getNID().equals(this.getNID()) == false)
					throw new IllegalStateException("NodeMassEntry NIDs do not match");

				for (int planck : other.plancks.keySet())
				{
					if (this.plancks.containsKey(planck) == true)
						this.plancks.put(planck, this.plancks.get(planck).add(other.plancks.get(planck)));
					else
						this.plancks.put(planck, other.plancks.get(planck));
				}

				this.partial = false;
				this.modified = true;
			}
			finally
			{
				this.lock.unlock();
			}
		}

		public NodeMass get()
		{
			return get(Integer.MAX_VALUE);
		}

		public NodeMass get(int planck)
		{
			this.lock.lock();
			try
			{
				UInt384 mass = UInt384.ZERO;
				for (Entry<Integer, UInt384> planckMass : this.plancks.entrySet())
				{
					if (planckMass.getKey() >= planck)
						continue;

					mass = mass.add(planckMass.getValue());
				}

				return new NodeMass(this.NID, mass, planck);
			}
			finally
			{
				this.lock.unlock();
			}
		}

		public void increment(int planck, UInt384 mass, long clock)
		{
			this.lock.lock();
			try
			{
				this.plancks.put(planck, this.plancks.getOrDefault(planck, UInt384.ZERO).add(mass));
				setModified(clock);
			}
			finally
			{
				this.lock.unlock();
			}
		}

		public void decrement(int planck, UInt384 mass, long clock)
		{
			this.lock.lock();
			try
			{
				this.plancks.put(planck, this.plancks.getOrDefault(planck, UInt384.ZERO).subtract(mass));
				setModified(clock);
			}
			finally
			{
				this.lock.unlock();
			}
		}

		@Override
		public String toString()
		{
			return this.NID + " Modified: "+this.modified+" Partial: "+this.partial;
		}

		public byte[] toByteArray() throws IOException
		{
			this.lock.lock();
			try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
				Writer writer = new Writer(baos);

				writer.writeEUID(this.NID);
				writer.writeInt(this.plancks.size());
				for (Map.Entry<Integer, UInt384> entry : this.plancks.entrySet()) {
					writer.writeInt(entry.getKey().intValue());
					writer.writeBytes(entry.getValue().toByteArray());
				}
				return baos.toByteArray();
			} finally {
				this.lock.unlock();
			}
		}

		public void fromByteArray(byte[] bytes) throws IOException
		{
			Reader reader = new Reader(bytes);

			this.NID = reader.readEUID();
			int numPlancks = reader.readInt();
			for (int p = 0 ; p < numPlancks ; p++)
				this.plancks.put(reader.readInt(), UInt384.from(reader.readBytes(UInt384.BYTES)));
		}
	}

	private Database nodeMassDatabase = null;
	private final Semaphore capacity;
	private long flushTime;
	private final AtomicBoolean flushing = new AtomicBoolean(false);
	private final AtomicBoolean flushPending = new AtomicBoolean(false);
	private final AtomicLong recovery = new AtomicLong(0l);
	private final Map<EUID, NodeMassEntry> nodes;

	public NodeMassStore()
	{
		super();

		this.capacity = new Semaphore(Modules.get(RuntimeProperties.class).get("tempo.mass.cache.capacity", 1024));
		this.nodes = new ConcurrentHashMap<>(capacity.availablePermits());
	}

	@Override
	public void start_impl() throws ModuleException
	{
		DatabaseConfig nodeMassConfig = new DatabaseConfig();
		nodeMassConfig.setAllowCreate(true);
		nodeMassConfig.setTransactional(true);
		nodeMassConfig.setKeyPrefixing(true);

		try
		{
			this.nodeMassDatabase = Modules.get(DatabaseEnvironment.class).getEnvironment().openDatabase(null, "tempo.node_mass", nodeMassConfig);

			byte[] data = Modules.get(DatabaseEnvironment.class).get(getName(), "recovery");
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
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "tempo.node_mass", false);

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

		this.nodes.clear();

		super.reset_impl();
	}

	@Override
	public void stop_impl() throws ModuleException
	{
		super.stop_impl();

		Events.getInstance().deregister(AtomEvent.class, this.atomListener);

		this.nodeMassDatabase.close();
	}

	@Override
	public void build() throws DatabaseException { }

	@Override
	public void maintenence() throws DatabaseException { }

	@Override
	public void integrity() throws DatabaseException
	{
		// TODO check integrity of NodeMass
	}

	@Override
	public String getName() { return "Node Mass Store"; }

	@Override
	public synchronized void flush()
	{
		if ((this.nodes.size() > this.capacity.availablePermits() || TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - this.flushTime) > 0) &&
				this.flushPending.compareAndSet(false, true) == true)
		{
			this.flushTime = System.currentTimeMillis();
			Executor.getInstance().submit(new Callable<NodeMassStore>()
			{
				@Override
				public NodeMassStore call() throws Exception
				{
					doFlush();
					return NodeMassStore.this;
				}
			});
		}
	}

	private void doFlush() throws DatabaseException {
		if (this.flushing.compareAndSet(false, true) == false)
			return;

		do
		{
			List<NodeMassEntry> nodeMassEntries = new ArrayList<>();
			NodeMassEntry nodeMassEntry = null;

			NodeMassStore.this.recovery.set(Long.MAX_VALUE);
			Iterator<NodeMassEntry> nodeMassEntryIterator = NodeMassStore.this.nodes.values().iterator();
			while (nodeMassEntryIterator.hasNext())
			{
				nodeMassEntry = nodeMassEntryIterator.next();

				if (nodeMassEntry.lock.tryLock() == true)
				{
					try
					{
						if (nodeMassEntry.isModified() == false)
						{
							nodeMassEntryIterator.remove();
							NodeMassStore.this.capacity.release();
							continue;
						}

						nodeMassEntries.add(nodeMassEntry);

						if (nodeMassEntry.modifiedAt.get() < NodeMassStore.this.recovery.get())
							NodeMassStore.this.recovery.set(nodeMassEntry.modifiedAt.get());
					}
					finally
					{
						nodeMassEntry.lock.unlock();
					}
				}
			}

			Collections.sort(nodeMassEntries);

			Transaction transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, null);
			try
			{
				DatabaseEntry key = new DatabaseEntry();
				DatabaseEntry data = new DatabaseEntry();
				DatabaseEntry serialized = new DatabaseEntry();

				nodeMassEntryIterator = nodeMassEntries.iterator();
				while (nodeMassEntryIterator.hasNext())
				{
					nodeMassEntry = nodeMassEntryIterator.next();

					nodeMassEntry.lock.lock();
					try
					{
						key.setData(nodeMassEntry.getNID().toByteArray());

						if (nodeMassEntry.isPartial() == true)
						{
							OperationStatus status = NodeMassStore.this.nodeMassDatabase.get(transaction, key, data, LockMode.RMW);

							if (status == OperationStatus.SUCCESS)
								nodeMassEntry.merge(new NodeMassEntry(data.getData()));
							else
								nodeMassEntry.setPartial(false);
						}

						nodeMassEntry.setNotModified();
						serialized.setData(nodeMassEntry.toByteArray());
						OperationStatus status = NodeMassStore.this.nodeMassDatabase.put(transaction, key, serialized);
						if (status != OperationStatus.SUCCESS)
							throw new IOException(status.name());
					}
					finally
					{
						nodeMassEntry.lock.unlock();
					}
				}

				Modules.get(DatabaseEnvironment.class).put(transaction, getName(), "recovery", Longs.toByteArray(NodeMassStore.this.recovery.get()));

				transaction.commit();
			}
			catch(Exception ex)
			{
				transaction.abort();
				DatabaseException dbex = new DatabaseException("Failed to store NodeMassEntry "+nodeMassEntry, ex);
				throw dbex;
			}
		}
		while (NodeMassStore.this.nodes.size() > NodeMassStore.this.capacity.availablePermits());

		this.flushing.set(false);
		this.flushPending.set(false);
	}

	public NodeMass getNodeMass(int planck, EUID NID)
	{
		return this.apply(NID, false, nodeMassEntry -> nodeMassEntry.get(planck));
	}

	// FIXME this is a very heavy function with lots of nodes, paginate it
	public Set<NodeMass> getNodeMasses(int planck) throws DatabaseException
	{
		Map<EUID, NodeMass> masses = new HashMap<>();

		try
		{
			Set<EUID> nodesInCache = new HashSet<>(this.nodes.keySet());
			for (EUID NID : nodesInCache)
				masses.put(NID, this.getNodeMass(Integer.MAX_VALUE, NID));

			try (Cursor cursor = this.nodeMassDatabase.openCursor(null, null)) {
				DatabaseEntry key = new DatabaseEntry();
				DatabaseEntry data = new DatabaseEntry();
				OperationStatus status;

				status = cursor.getFirst(key, data, LockMode.DEFAULT);
				if (status != OperationStatus.NOTFOUND)
				{
					do
					{
						EUID NID = new EUID(key.getData());

						if (masses.containsKey(NID) == false)
							masses.put(NID, new NodeMassEntry(data.getData()).get());

						status = cursor.getNext(key, data, LockMode.DEFAULT);
					}
					while(status == OperationStatus.SUCCESS);
				}
			}
		}
		catch (Throwable t)
		{
			throw new DatabaseException(t);
		}

		return new HashSet<>(masses.values());
	}

	private void incrementNodeMass(PreparedAtom preparedAtom) {
		final Atom atom = preparedAtom.getAtom();

		int planck = Modules.get(Universe.class).toPlanck(atom.getTimestamp(), Offset.NEXT);
		long clock = atom.getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID()).getClock();
		EUID origin = atom.getTemporalProof().getOrigin().getOwner().getUID();
		final UInt384 mass = preparedAtom.getMass();

		if (!mass.isZero()) {
			this.apply(origin, true, nodeMassEntry -> {
				nodeMassEntry.increment(planck, mass, clock);
				return null;
			});
		}
	}

	private void decrementNodeMass(PreparedAtom preparedAtom) {
		final Atom atom = preparedAtom.getAtom();

		int planck = Modules.get(Universe.class).toPlanck(atom.getTimestamp(), Offset.NEXT);
		long clock = atom.getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID()).getClock();
		final EUID origin = atom.getTemporalProof().getOrigin().getOwner().getUID();
		final UInt384 mass = preparedAtom.getMass();

		if (!mass.isZero()) {
			this.apply(origin, true, nodeMassEntry -> {
				nodeMassEntry.decrement(planck, mass, clock);
				return null;
			});
		}
	}

	private <R> R apply(EUID NID, boolean partial, java.util.function.Function<NodeMassEntry, R> function)
	{
		AtomicBoolean needAcquire = new AtomicBoolean(false);	// FIXME this is shitty!
		NodeMassEntry nodeMassEntry = null;

		nodeMassEntry = this.nodes.computeIfAbsent(NID, c -> {
				needAcquire.set(true);
				return new NodeMassEntry(NID);
		});

		nodeMassEntry.lock.lock();
		try
		{
			if (nodeMassEntry.isPartial() == true && partial == false)
			{
				DatabaseEntry data = new DatabaseEntry();
				OperationStatus status = this.nodeMassDatabase.get(null, new DatabaseEntry(NID.toByteArray()), data, LockMode.DEFAULT);

				if (status == OperationStatus.SUCCESS)
				{
					try
					{
						nodeMassEntry.merge(new NodeMassEntry(data.getData()));
					}
					catch (IOException ioex)
					{
						atomsLog.error("Reading of NodeMassEntry "+NID+" for merge failed", ioex);
						nodeMassEntry.setPartial(false);
					}
				}
				else
					nodeMassEntry.setPartial(false);
			}

			R result = function.apply(nodeMassEntry);
			return result;
		}
		finally
		{
			nodeMassEntry.lock.unlock();

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

	// ATOM LISTENER //
	private AtomListener atomListener = event -> {
		try {
			if (event instanceof AtomStoredEvent) {
				AtomStoredEvent storedEvent = (AtomStoredEvent) event;
				if (storedEvent.getAtom().getTemporalProof().isEmpty()) {
					throw new AssertionError("Atom " + storedEvent.getAtom() + " stored but no Temporal Proof");
				}

				NodeMassStore.this.incrementNodeMass(storedEvent.getPreparedAtom());
			}

			if (event instanceof AtomUpdatedEvent) {
				// TODO how to handle these if TPs have merged or even changed, do we need to even?
			}

			if (event instanceof AtomDeletedEvent) {
				AtomDeletedEvent deletedEvent = (AtomDeletedEvent) event;
				if (deletedEvent.getAtom().getTemporalProof().isEmpty()) {
					throw new AssertionError("Atom " + deletedEvent.getAtom() + " deleted but no Temporal Proof");
				}

				NodeMassStore.this.decrementNodeMass(deletedEvent.getPreparedAtom());
			}
		} catch (Exception ex) {
			log.error("Failed to update NodeMassStore on " + event.getClass().getName() + " for " + event.getAtom(), ex);
		}
	};
}