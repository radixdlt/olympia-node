package org.radix.atoms;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.radixdlt.ledger.LedgerSearchMode;
import org.bouncycastle.util.Arrays;
import org.radix.atoms.events.AtomDeletedEvent;
import org.radix.atoms.events.AtomStoredEvent;
import org.radix.atoms.events.AtomUpdatedEvent;
import org.radix.common.executors.ScheduledExecutable;
import org.radix.database.DBAction;
import org.radix.database.DatabaseEnvironment;
import org.radix.database.DatabaseStore;
import org.radix.database.exceptions.DatabaseException;
import org.radix.database.exceptions.KeyExistsDatabaseException;
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
import org.radix.shards.ShardRange;
import org.radix.shards.ShardSpace;
import org.radix.time.TemporalVertex;
import org.radix.time.Timestamps;
import org.radix.universe.system.CommitmentCollector;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.SystemProfiler;

import com.google.common.collect.Lists;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.common.Pair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerCursor.Type;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationUtils;
import com.radixdlt.tempo.TempoCursor;
import com.radixdlt.utils.Longs;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.DiskOrderedCursor;
import com.sleepycat.je.DiskOrderedCursorConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.SecondaryMultiKeyCreator;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.UniqueConstraintException;

public class AtomStore extends DatabaseStore implements DiscoverySource<AtomDiscoveryRequest>
{
	private static final Logger atomsLog = Logging.getLogger ("atoms");

	public enum IDType
	{
		ATOM, PARTICLE_UP, PARTICLE_DOWN, PARTICLE_CLASS, UID, DESTINATION, SHARD;

		public static byte[] toByteArray(IDType type, long value)
		{
			byte[] typeBytes = new byte[Long.BYTES+1];
			typeBytes[0] = (byte) type.ordinal();
			System.arraycopy(Longs.toByteArray(value), 0, typeBytes, 1, Long.BYTES);
			return typeBytes;
		}

		public static byte[] toByteArray(IDType type, EUID id)
		{
			if (id == null)
				throw new IllegalArgumentException("EUID is null");

			byte[] idBytes = id.toByteArray();
			byte[] typeBytes = new byte[idBytes.length+1];
			typeBytes[0] = (byte) type.ordinal();
			System.arraycopy(idBytes, 0, typeBytes, 1, idBytes.length);
			return typeBytes;
		}

		public static byte[] toByteArray(IDType type, AID aid) {
			if (aid == null)
				throw new IllegalArgumentException("AID is null");

			byte[] typeBytes = new byte[AID.BYTES + 1];
			typeBytes[0] = (byte) type.ordinal();
			aid.copyTo(typeBytes, 1);
			return typeBytes;
		}

		public static EUID toEUID(byte[] bytes)
		{
			byte[] temp = new byte[bytes.length-1];
			System.arraycopy(bytes, 1, temp, 0, temp.length);
			return new EUID(temp);
		}

		public static long toLong(byte[] bytes)
		{
			byte[] temp = new byte[bytes.length-1];
			System.arraycopy(bytes, 1, temp, 0, temp.length);
			return Longs.fromByteArray(temp);
		}
	}

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

	private Database			atomsDatabase;
	private SecondaryDatabase	uniqueIndexables;
	private SecondaryDatabase	duplicatedIndexables;

	private AtomStoreBloom		atomsIDBloom;
	private final Map<Long, PreparedAtom> processing = new ConcurrentHashMap<Long, PreparedAtom>();

	private class AtomMultipleSecondaryKeyCreator implements SecondaryMultiKeyCreator
	{
		@Override
		public void createSecondaryKeys(SecondaryDatabase database, DatabaseEntry key, DatabaseEntry value, Set<DatabaseEntry> secondaries)
		{
			PreparedAtom preparedAtom = AtomStore.this.processing.get(Longs.fromByteArray(value.getData()));

			if (preparedAtom == null)
				try
				{
					preparedAtom = new PreparedAtom(value.getData());
					atomsLog.warn("AtomData for "+preparedAtom.getAtomID()+" not in processing set");
				}
				catch (IOException ioex)
				{
					throw new IllegalStateException(ioex);
				}

			if (database == AtomStore.this.uniqueIndexables)
			{
				for (byte[] indexableKey : preparedAtom.getUniqueIndexables())
					secondaries.add(new DatabaseEntry(indexableKey));
			}

			if (database == AtomStore.this.duplicatedIndexables)
			{
				for (byte[] indexableKey : preparedAtom.getDuplicateIndexables())
					secondaries.add(new DatabaseEntry(indexableKey));
			}
		}
	}

	public AtomStore()
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
		primaryConfig.setBtreeComparator(AtomStore.AtomStorePackedPrimaryKeyComparator.class);

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
			this.atomsDatabase = Modules.get(DatabaseEnvironment.class).getEnvironment().openDatabase(null, "tempo.atoms", primaryConfig);
			this.uniqueIndexables = Modules.get(DatabaseEnvironment.class).getEnvironment().openSecondaryDatabase(null, "tempo.unique_indexables", this.atomsDatabase, uniqueIndexablesConfig);
			this.duplicatedIndexables = Modules.get(DatabaseEnvironment.class).getEnvironment().openSecondaryDatabase(null, "tempo.duplicated_indexables", this.atomsDatabase, duplicatedIndexablesConfig);

			// Disabled by default, as does not seem to have any long-term performance improvements as found during 1M TPS testing.
			boolean useAtomBloom = Modules.get(RuntimeProperties.class).get("atoms.bloom", false);

			if (useAtomBloom) {
				File atomBloomFile = new File(Modules.get(RuntimeProperties.class).get("db.location", ".//RADIXDB")+"//atoms.bloom");
				if (atomBloomFile.exists()) {
					this.atomsIDBloom = tryLoadingAtomStoreBloom(atomBloomFile);
				}
				if (this.atomsIDBloom == null) {
					log.info("Creating 'atoms.bloom'");
					this.atomsIDBloom = new AtomStoreBloom(0.01, 200000000, "Atom HID Bloom");
				}
			}
		}
        catch (Exception ex)
        {
        	throw new ModuleStartException(ex, this);
		}

		// ATOMHIDBLOOM WRITER //
		scheduleAtFixedRate(new ScheduledExecutable(1, 10, TimeUnit.MINUTES)
		{
			@Override
			public void execute()
			{
				long start = SystemProfiler.getInstance().begin();

				try
				{
					if (AtomStore.this.atomsIDBloom != null)
					{
						synchronized(AtomStore.this.atomsIDBloom)
						{
							File atomBloomTempFile = new File(Modules.get(RuntimeProperties.class).get("db.location", ".//RADIXDB")+"//atoms.bloom.temp");

							try (FileOutputStream atomBloomTempStream = new FileOutputStream(atomBloomTempFile)) {
								AtomStore.this.atomsIDBloom.serialize(atomBloomTempStream);
							}

							File atomBloomFile = new File(Modules.get(RuntimeProperties.class).get("db.location", ".//RADIXDB")+"//atoms.bloom");

							if (atomBloomFile.exists() && !atomBloomFile.delete())
								throw new IOException("Could not delete existing 'atoms.bloom'");

							if (!atomBloomTempFile.renameTo(atomBloomFile))
								throw new IOException("Could not rename temporary 'atoms.bloom.temp'");
						}
					}
				}
				catch (Exception ex)
				{
					log.error(ex);
				}
				finally
				{
					SystemProfiler.getInstance().incrementFrom("ATOM_STORE:STORE_ATOM_HID_BLOOM", start);
				}
			}
		});

		super.start_impl();
	}

	private AtomStoreBloom tryLoadingAtomStoreBloom(File atomBloomFile) {
		log.info("Loading 'atoms.bloom'");
		try (FileInputStream atomBloomStream = new FileInputStream(atomBloomFile)) {
			return new AtomStoreBloom(atomBloomStream);
		} catch (IOException ex) {
			log.error("Atom Bloom filter is corrupt, requires rebuilding", ex);
			if (atomBloomFile.delete()) {
				log.info("Removed Atom Bloom filter file " + atomBloomFile.toString());
			}
			return null;
		}
	}

	@Override
	public void reset_impl() throws ModuleException
	{
		Transaction transaction = null;

		try
		{
			Modules.get(DatabaseEnvironment.class).lock();

			transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "tempo.atoms", false);
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "tempo.unique_indexables", false);
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "tempo.duplicated_indexables", false);

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
		long start = SystemProfiler.getInstance().begin();

		Cursor cursor = null;

		try
		{
			log.info("Recovering LocalSystem state from clock "+LocalSystem.getInstance().getClock().get());

			DatabaseEntry pKey = new DatabaseEntry(Longs.toByteArray(LocalSystem.getInstance().getClock().get()));
			DatabaseEntry data = new DatabaseEntry();

			cursor = this.atomsDatabase.openCursor(null, null);
			OperationStatus status = cursor.getSearchKey(pKey, data, LockMode.DEFAULT);

			if (status != OperationStatus.SUCCESS)
				status = cursor.getSearchKeyRange(pKey, data, LockMode.DEFAULT);

			while(status == OperationStatus.SUCCESS)
			{
				PreparedAtom preparedAtom = new PreparedAtom(data.getData());
				LocalSystem.getInstance().update(preparedAtom.getAtom().getAID(), preparedAtom.getAtom().getTimestamp());

				status = cursor.getNext(pKey, data, LockMode.DEFAULT);
			}

			log.info("Recovered LocalSystem state to clock "+LocalSystem.getInstance().getClock().get());
		}
		catch (Exception ex)
		{
			throw new DatabaseException("Could not recover LocalSystem state", ex);
		}
		finally
		{
			if (cursor != null)
				cursor.close();

			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:INTEGRITY:LOCAL_SYSTEM_CLOCK", start);
		}

		// TODO check this still functions
		if (Modules.get(RuntimeProperties.class).get("debug.system.state.integrity", true).equals(true))
		{
			cursor = null;

			start = System.nanoTime();

			try
			{
				log.info("Starting system state integrity process");

				long collectorClock = 1;
				CommitmentCollector collector = new CommitmentCollector(Hash.BITS);
				DatabaseEntry collectorKey = new DatabaseEntry(Longs.toByteArray(collectorClock));
				DatabaseEntry collectorData = new DatabaseEntry();

				cursor = this.atomsDatabase.openCursor(null, null);

				OperationStatus status = cursor.getSearchKeyRange(collectorKey, collectorData, LockMode.DEFAULT);
				while(status == OperationStatus.SUCCESS)
				{
					PreparedAtom preparedAtom = new PreparedAtom(collectorData.getData());
					TemporalVertex temporalVertex = preparedAtom.getAtom().getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID());

					if (temporalVertex != null)
					{
						collector.put(temporalVertex.getClock(), temporalVertex.getCommitment().toByteArray());

						if (temporalVertex.getClock() - Hash.BITS > 0)
						{
							DatabaseEntry integrityKey = new DatabaseEntry();
							DatabaseEntry integrityData = new DatabaseEntry();

							while(collectorClock < temporalVertex.getClock() - Hash.BITS)
							{
								if (collectorClock%1000 == 0)
									log.info("System state integrity progress ..."+collectorClock);

								integrityKey.setData(Longs.toByteArray(collectorClock));

								if (this.atomsDatabase.get(null, integrityKey, integrityData, LockMode.DEFAULT) == OperationStatus.SUCCESS)
								{
									PreparedAtom atomData256 = new PreparedAtom(integrityData.getData());
									int threshold = collector.has(atomData256.getAtom().getHash().toByteArray());
									if (threshold == 0)
										log.error("Atom "+atomData256.getAtomID()+":"+atomData256.getAtom().getHash()+" @ "+collectorClock+" is not encoded into commitments");
									else if (threshold < collector.length())
										log.error("Atom "+atomData256.getAtomID()+":"+atomData256.getAtom().getHash()+" @ "+collectorClock+" present in commitments with threshold "+threshold);
								}
								else
								{
									Pair<byte[], Integer> prediction = collector.get(collectorClock);
									Hash predictedHash = new Hash(prediction.getFirst());
									log.error("Atom @ "+collectorClock+" is not present in AtomStore. Predicted to be "+predictedHash+":"+predictedHash.getID()+" with "+prediction.getSecond()+"/"+Hash.BITS+" certainty");
								}

								collector.remove(collectorClock);
								collectorClock++;
							}
						}
					}

					status = cursor.getNext(collectorKey, collectorData, LockMode.DEFAULT);
				}

				log.info("Completed system state integrity process");
			}
			catch (Exception ex)
			{
				throw new DatabaseException("System state integrity process failed", ex);
			}
			finally
			{
				if (cursor != null)
					cursor.close();

				SystemProfiler.getInstance().incrementFrom("ATOM_STORE:INTEGRITY:LOCAL_SYSTEM_STATE", start);
			}
		}

		if (this.atomsIDBloom != null)
		{
			long bloomProgress = 0;

			log.info("Starting bloom filter integrity process");

			DiskOrderedCursor diskCursor = null;

			try
			{
				DiskOrderedCursorConfig diskCursorConfig = new DiskOrderedCursorConfig();
				diskCursorConfig.setInternalMemoryLimit((long) (Runtime.getRuntime().maxMemory()*0.1));
				diskCursorConfig.setQueueSize((int) ((Runtime.getRuntime().maxMemory()*0.1)/8192));
				diskCursor = this.atomsDatabase.openCursor(diskCursorConfig);

				DatabaseEntry pKey = new DatabaseEntry();
				DatabaseEntry data = new DatabaseEntry();

				while(diskCursor.getNext(pKey, data, LockMode.DEFAULT) == OperationStatus.SUCCESS)
				{
					bloomProgress++;

					if (bloomProgress%1000 == 0)
						log.info("Bloom filter integrity progress ..."+bloomProgress);

					PreparedAtom preparedAtom = new PreparedAtom(data.getData());

					if (preparedAtom.getClock() > this.atomsIDBloom.getClock() &&
						this.atomsIDBloom.contains(preparedAtom.getAtomID().getBytes()) == false)
					{
							for (byte[] indexableKey : preparedAtom.getUniqueIndexables())
								this.atomsIDBloom.add(indexableKey, preparedAtom.getClock());
					}
				}

				File atomBloomFile = new File(Modules.get(RuntimeProperties.class).get("db.location", ".//RADIXDB")+"//atoms.bloom");
				try (FileOutputStream atomBloomStream = new FileOutputStream(atomBloomFile)) {
					this.atomsIDBloom.serialize(atomBloomStream);
				}

				log.info("Completed bloom filter integrity process");
			}
			catch (Exception ex)
			{
				throw new DatabaseException("Bloom filter integrity process failed", ex);
			}
			finally
			{
				diskCursor.close();
			}
		}
	}

	@Override
	public void flush() throws DatabaseException { /* Not used */ }

	@Override
	public String getName()
	{
		return "Atom Store";
	}

	// ATOMS //
	public void dumpAtoms(boolean verbose) throws DatabaseException
	{
		long dumpProgress = 0;
		File atomsDumpFile = new File(Modules.get(RuntimeProperties.class).get("db.location", ".//RADIXDB")+"//atoms_"+System.currentTimeMillis()+".dump");

		try (PrintStream atomsDumpStream = new PrintStream(atomsDumpFile);
			 Cursor cursor = this.atomsDatabase.openCursor(null, null)) {

			DatabaseEntry pKey = new DatabaseEntry(Longs.toByteArray(1l));
			DatabaseEntry data = verbose == true ? new DatabaseEntry() : null;
			OperationStatus status = cursor.getFirst(pKey, data, LockMode.DEFAULT);

			while(status == OperationStatus.SUCCESS)
			{
				dumpProgress++;

				if (dumpProgress%1000 == 0)
					atomsLog.info("Dumping Atoms..."+dumpProgress);

				atomsDumpStream.print("CLOCK: "+Longs.fromByteArray(pKey.getData())+" ");
				atomsDumpStream.print(" ATOM: "+new EUID(pKey.getData(), Long.BYTES)+" ");

				if (verbose)
				{
					PreparedAtom preparedAtom = new PreparedAtom(data.getData());

					Set<EUID> spinUps = new HashSet<>();
					Set<EUID> spinDowns = new HashSet<>();

					for (byte[] indexableKey : preparedAtom.getUniqueIndexables())
					{
						if (indexableKey[0] == IDType.PARTICLE_UP.ordinal())
							spinUps.add(IDType.toEUID(indexableKey));
						else if (indexableKey[0] == IDType.PARTICLE_DOWN.ordinal())
							spinDowns.add(IDType.toEUID(indexableKey));
					}

					if (spinUps.isEmpty() == false)
						atomsDumpStream.print(" SPIN_UP: "+ spinUps.stream().map(EUID::toString).collect(Collectors.joining(",")));

					if (spinDowns.isEmpty() == false)
						atomsDumpStream.print(" SPIN_DOWN: "+ spinDowns.stream().map(EUID::toString).collect(Collectors.joining(",")));

					atomsDumpStream.print(" TEMPORAL: "+ preparedAtom.getAtom().getTemporalProof().getVertices().stream().map(TemporalVertex::toString).collect(Collectors.joining(",")));
				}

				atomsDumpStream.println();

				if ((status = cursor.getNextDup(pKey, data, LockMode.DEFAULT)) == OperationStatus.SUCCESS)
				{
					System.out.println("DUP WHAT??");
				}
				else
					status = cursor.getNext(pKey, data, LockMode.DEFAULT);
			}
		}
		catch (Exception ex)
		{
			throw new DatabaseException("Could not dump Atoms", ex);
		}
	}

	public DBAction deleteAtoms(Atom atom) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		try
        {
			List<Atom> atoms = new ArrayList<Atom>();

			atoms.add(atom);
			atoms.addAll(getAtomDependents(atom));

			atoms.sort(Comparator.comparingLong(Atom::getTimestamp).reversed());

			for (Atom a : atoms)
				deleteAtom(a);

			return new DBAction(DBAction.DELETE, atoms, true);
        }
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:DELETE_ATOMS", start);
		}
	}

	public DBAction deleteAtoms(AID AID) throws DatabaseException
	{
		Optional<Atom> atom = this.getAtom(AID);
		
		if (!atom.isPresent())
			return new DBAction(DBAction.DELETE, AID, false);
		
		return this.deleteAtoms(atom.get());
	}

	public DBAction deleteAtom(AID AID) throws DatabaseException
	{
		Optional<Atom> atom = this.getAtom(AID);
		
		if (!atom.isPresent())
			return new DBAction(DBAction.DELETE, AID, false);
		
		return this.deleteAtom(atom.get());
	}
	
	public DBAction deleteAtom(Atom atom) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		PreparedAtom preparedAtom = null;
		DatabaseEntry pKey = new DatabaseEntry();
		DatabaseEntry key = new DatabaseEntry(IDType.toByteArray(IDType.ATOM, atom.getAID()));
		DatabaseEntry data = new DatabaseEntry();
		Transaction transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, null);

		try
        {
			OperationStatus status;

			if ((status = this.uniqueIndexables.get(transaction, key, pKey, data, LockMode.RMW)) == OperationStatus.SUCCESS)
			{
				preparedAtom = new PreparedAtom(data.getData());
				this.processing.put(preparedAtom.getClock(), preparedAtom);

				if ((status = this.atomsDatabase.delete(transaction, pKey)) != OperationStatus.SUCCESS)
					throw new DatabaseException("Can not delete Atom "+atom.getAID()+" due to OperationStatus "+status);

				transaction.commit();

				Events.getInstance().broadcastWithException(new AtomDeletedEvent(preparedAtom));
				return new DBAction(DBAction.DELETE, atom, true);
			}
			else
			{
				transaction.abort();
				return new DBAction(DBAction.DELETE, atom, false);
			}
		}
		catch (Throwable t)
		{
			transaction.abort();

			if (t instanceof DatabaseException)
				throw (DatabaseException)t;
			else
				throw new DatabaseException(t);
		}
		finally
		{
			if (preparedAtom != null)
				this.processing.remove(preparedAtom.getClock());

			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:DELETE_ATOM", start);
		}
	}

	// TODO this should be package private once we're refactored the Tempo packages
	public Atom getLastAtom() throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		try (Cursor cursor = this.atomsDatabase.openCursor(null, null)) {

			DatabaseEntry key = new DatabaseEntry();
			DatabaseEntry data = new DatabaseEntry();

			if (cursor.getLast(key, data, LockMode.DEFAULT) == OperationStatus.SUCCESS)
			{
				PreparedAtom preparedAtom = new PreparedAtom(data.getData());
				Atom atom = preparedAtom.getAtom();
				return atom;
			}

			return null;
		}
		catch (DatabaseException dbex)
		{
			throw dbex;
		}
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:GET_LAST_ATOM", start);
		}
	}


	public PreparedAtom getAtom(long clock) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		try
        {
			DatabaseEntry key = new DatabaseEntry(Longs.toByteArray(clock));
			DatabaseEntry data = new DatabaseEntry();

			if (this.atomsDatabase.get(null, key, data, LockMode.DEFAULT) == OperationStatus.SUCCESS)
			{
				PreparedAtom preparedAtom = new PreparedAtom(data.getData());
				return preparedAtom;
			}
		}
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:GET_ATOM", start);
		}

		return null;
	}

	public boolean hasAtom(long clock) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		try
        {
			DatabaseEntry key = new DatabaseEntry(Longs.toByteArray(clock));

			if (this.atomsDatabase.get(null, key, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
				return true;
		}
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:HAS_ATOM", start);
		}

		return false;
	}

	public Optional<Atom> getAtom(AID id) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		try
        {
			DatabaseEntry key = new DatabaseEntry(IDType.toByteArray(IDType.ATOM, id));
			DatabaseEntry data = new DatabaseEntry();

			if (this.uniqueIndexables.get(null, key, data, LockMode.DEFAULT) == OperationStatus.SUCCESS)
			{
				PreparedAtom preparedAtom = new PreparedAtom(data.getData());
				return Optional.of(preparedAtom.getAtom());
			}
		}
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:GET_ATOM", start);
		}

		return Optional.empty();
	}

	public List<Atom> getAtoms(Collection<AID> ids) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		List<Atom> atoms = new ArrayList<Atom>();

		try
        {
			for (AID id : ids)
			{
				Optional<Atom> atom = getAtom(id);

				if (atom.isPresent()) {
					atoms.add(atom.get());
				}
			}
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:GET_ATOMS", start);
		}

		return atoms.isEmpty()?null:atoms;
	}

	private Collection<Atom> getAtomDependents(Atom atom) throws DatabaseException
	{
		LinkedHashSet<Atom> dependents = new LinkedHashSet<>();
		LinkedList<Particle> dependables = new LinkedList<>(getAtomDependables(atom));

		while (!dependables.isEmpty()) {
			final Atom dependentAtom = getAtomContaining(dependables.removeFirst(), Spin.DOWN);

			if (dependentAtom != null && !dependentAtom.equals(atom) && !dependents.contains(dependentAtom)) {
				dependents.add(dependentAtom);
				dependables.addAll(getAtomDependables(dependentAtom));
			}
			// TODO associated atoms
		}

		return dependents;
	}

	// TODO this is a super hack, try the abstract this via an interface!
	private List<Particle> getAtomDependables(Atom atom) {
		// Included conversation from merge request which may be illuminating.
		//
		// Florian Cäsar @florian  commented about an hour ago
		// Why only 'UP' particles? Because they are considered 'consumable' in some form?
		//
		// Martin Sandiford @martin  commented about an hour ago
		// Because UP particles are the only particles that can create a downstream dependency.
		// This is used in the getAtomDependents(...) method above to work out what particles
		// from the current atom could potentially create a downstream dependency, and then
		// determine whether a  dependency actually exists.
		return atom.particles(Spin.UP)
				.collect(Collectors.toList());
	}

	public Atom getAtomContaining(Particle particle, Spin spin) throws DatabaseException {
		long start = SystemProfiler.getInstance().begin();

		try
        {
			DatabaseEntry key = null;
			DatabaseEntry data = new DatabaseEntry();

			switch (spin)
			{
			case UP:
				key = new DatabaseEntry(IDType.toByteArray(IDType.PARTICLE_UP, particle.getHID()));
				break;
			case DOWN:
				key = new DatabaseEntry(IDType.toByteArray(IDType.PARTICLE_DOWN, particle.getHID()));
				break;
			default:
				return null;
			}

			if (this.atomsIDBloom != null && !this.atomsIDBloom.contains(key.getData())) {
				return null;
			}

			OperationStatus status = this.uniqueIndexables.get(null, key, data, LockMode.DEFAULT);

			if (status == OperationStatus.SUCCESS)
			{
				PreparedAtom preparedAtom = new PreparedAtom(data.getData());

				Optional<Particle> foundParticle = preparedAtom.getAtom().particles(spin) .filter(p -> p.getHID().equals(particle.getHID())) .findFirst();

				if (foundParticle.isPresent())
					return preparedAtom.getAtom();
			}
		}
		catch (DatabaseException dbex)
		{
			throw dbex;
		}
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:GET_ATOM_CONTAINING", start);
		}

		return null;
	}

	public Spin getSpin(Particle particle) throws DatabaseException {
		if (hasAtomContaining(particle, Spin.DOWN)) {
			return Spin.DOWN;
		} else if (hasAtomContaining(particle, Spin.UP)) {
			return Spin.UP;
		} else {
			return Spin.NEUTRAL;
		}
	}

	public boolean hasAtomContaining(Particle particle, Spin spin) throws DatabaseException {
		long start = SystemProfiler.getInstance().begin();

		try
        {
			DatabaseEntry key = null;

			switch (spin)
			{
			case UP:
				key = new DatabaseEntry(IDType.toByteArray(IDType.PARTICLE_UP, particle.getHID()));
				break;
			case DOWN:
				key = new DatabaseEntry(IDType.toByteArray(IDType.PARTICLE_DOWN, particle.getHID()));
				break;
			default:
				return false;
			}

			if (this.atomsIDBloom != null && this.atomsIDBloom.contains(key.getData()) == false)
				return false;

			OperationStatus status = this.uniqueIndexables.get(null, key, null, LockMode.DEFAULT);

			if (status == OperationStatus.SUCCESS)
				return true;
			else
				return false;
		}
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:HAS_ATOM_CONTAINING", start);
		}
	}

	public boolean hasAtom(AID id) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();
        boolean falsePositive = false;

		try
        {
			DatabaseEntry key = new DatabaseEntry(IDType.toByteArray(IDType.ATOM, id));
			if (this.atomsIDBloom != null)
			{
				if (this.atomsIDBloom.contains(key.getData()))
				{
					OperationStatus status = this.uniqueIndexables.get(null, key, null, LockMode.DEFAULT);

					if (status == OperationStatus.SUCCESS)
						return true;
					else
				        falsePositive = true;
				}
			}
			else if (OperationStatus.SUCCESS == this.uniqueIndexables.get(null, key, null, LockMode.DEFAULT))
				return true;

			return false;
		}
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:HAS_ATOM"+(falsePositive==true?"|FALSE_POSITIVE":""), start);
		}
	}

	public List<AID> hasAtoms(Collection<AID> ids) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		List<AID> atomIds = new ArrayList<>();

		try
        {
			for (AID id : ids)
			{
				if (hasAtom(id))
					atomIds.add(id);
			}
		}
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:HAS_ATOMS", start);
		}

		return atomIds.isEmpty()?null:atomIds;
	}

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

			DatabaseEntry key = new DatabaseEntry(IDType.toByteArray(IDType.SHARD, from));
			DatabaseEntry data = new DatabaseEntry();

			// FIXME this is slow as shit, speed it up, maybe pack clock+HID for primary and use that
			OperationStatus status = cursor.getSearchKeyRange(key, data, LockMode.DEFAULT);
			while (status == OperationStatus.SUCCESS)
			{
				long shard = IDType.toLong(key.getData());
				if (shard < from || shard > to)
					break;

				PreparedAtom preparedAtom = new PreparedAtom(data.getData());
				atomIds.add(preparedAtom.getAtomID());

				status = cursor.getNextDup(key, data, LockMode.DEFAULT);
				if (status == OperationStatus.NOTFOUND)
					status = cursor.getNext(key, data, LockMode.DEFAULT);
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

			DatabaseEntry key = new DatabaseEntry(IDType.toByteArray(IDType.SHARD, shard));
			DatabaseEntry data = new DatabaseEntry();

			// FIXME this is slow as shit, speed it up, maybe pack clock+HID for primary and use that
			OperationStatus status = cursor.getSearchKey(key, data, LockMode.DEFAULT);
			while (status == OperationStatus.SUCCESS)
			{
				PreparedAtom preparedAtom = new PreparedAtom(data.getData());
				atomIds.add(preparedAtom.getAtomID());
				status = cursor.getNextDup(key, data, LockMode.DEFAULT);
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

	public synchronized DBAction storeAtom(final PreparedAtom preparedAtom) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		DatabaseEntry pKey = null;
		DatabaseEntry data = null;
		Transaction transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, null);

		try
        {
			OperationStatus status;
			pKey = new DatabaseEntry(Arrays.concatenate(Longs.toByteArray(preparedAtom.getClock()), preparedAtom.getAtomID().getBytes()));
			data = new DatabaseEntry(preparedAtom.toByteArray());
			this.processing.put(preparedAtom.getClock(), preparedAtom);

			status = this.atomsDatabase.putNoOverwrite(transaction, pKey, data);
			if (!status.equals(OperationStatus.SUCCESS))
			{
				if (status.equals(OperationStatus.KEYEXIST))
					throw new KeyExistsDatabaseException(this.atomsDatabase.getDatabaseName(), pKey);
				else
					throw new DatabaseException("Failed to store Atom "+preparedAtom.getAtom()+" due to "+status.name());
			}

			transaction.commit();

			if (this.atomsIDBloom != null)
				for (byte[] indexableKey : preparedAtom.getUniqueIndexables())
					this.atomsIDBloom.add(indexableKey, preparedAtom.getClock());

			Events.getInstance().broadcastWithException(new AtomStoredEvent(preparedAtom));

			return new DBAction("store", preparedAtom.getAtom(), true);
		}
		catch (Throwable t)
		{
			transaction.abort();

			if (t instanceof DatabaseException)
				throw (DatabaseException)t;
			else if (t instanceof UniqueConstraintException)
				throw new KeyExistsDatabaseException(this.atomsDatabase.getDatabaseName(), ((UniqueConstraintException)t).getSecondaryKey());
			else
				throw new DatabaseException(t);
		}
		finally
		{
			if (data != null && data.getData() != null && data.getData().length > 0)
				this.processing.remove(preparedAtom.getClock());

			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:STORE_ATOM", start);
		}
	}

	/**
	 * Updates an Atom in the AtomStore.
	 * <br><br>
	 * In practice only the Atom meta data is updated, the Atom itself should be unmodified.
	 *
	 * @param atom
	 * @return
	 * @throws DatabaseException
	 */
	public DBAction updateAtom(final Atom atom) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		PreparedAtom preparedAtom = null;
		OperationStatus status = OperationStatus.SUCCESS;
		DatabaseEntry pKey = new DatabaseEntry();
		DatabaseEntry key = new DatabaseEntry(IDType.toByteArray(IDType.ATOM, atom.getAID()));
		DatabaseEntry data = new DatabaseEntry();
		Transaction transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, null);

		try
		{
			if ((status = this.uniqueIndexables.get(transaction, key, pKey, data, LockMode.RMW)) == OperationStatus.SUCCESS)
			{
				preparedAtom = new PreparedAtom(data.getData());
				final long clock = preparedAtom.getClock();
				if (atom.getTemporalProof().getVertexByNID(LocalSystem.getInstance().getNID()).getClock() != clock)
					throw new IllegalStateException("Can not update Atom "+atom.getAID()+" due to mismatching clock");
			}
			else
				throw new DatabaseException("Can not update Atom "+atom.getAID()+" due to OperationStatus "+status);

			data = new DatabaseEntry(preparedAtom.toByteArray());

			this.processing.put(preparedAtom.getClock(), preparedAtom);
			this.atomsDatabase.put(transaction, pKey, data);

			transaction.commit();

			Events.getInstance().broadcastWithException(new AtomUpdatedEvent(preparedAtom));

			return new DBAction(DBAction.UPDATE, atom, true);
		}
		catch (Throwable t)
		{
			transaction.abort();

			if (t instanceof DatabaseException)
				throw (DatabaseException)t;
			else
				throw new DatabaseException(t);
		}
		finally
		{
			if (preparedAtom != null)
				this.processing.remove(preparedAtom.getClock());

			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:UPDATE_ATOM", start);
		}
	}

	// FIXME Make atomic
	public DBAction replaceAtom(Set<AID> aids, PreparedAtom preparedAtom) throws DatabaseException
	{
		for (AID aid : aids) {
			this.deleteAtom(aid);
		}
		return this.storeAtom(preparedAtom);
	}

	// DISCOVERY SOURCE //
	@Override
	public void discovery(AtomDiscoveryRequest request) throws DiscoveryException
	{
		if (request.getAction().equals(Action.DISCOVER) || request.getAction().equals(Action.DISCOVER_AND_DELIVER))
			query(request);

		if (request.getAction().equals(Action.DELIVER) || request.getAction().equals(Action.DISCOVER_AND_DELIVER))
			fetch(request);
	}

	// FIXME DPH: Fix urgently post AtomStore database modifications are completed
	@Override
	public void query(AtomDiscoveryRequest request) throws DiscoveryException
	{
		if (request.getUID().isZero() && request.getDestination().isZero() && request.getAID().isZero()) {
			doAtomClockDiscovery(request);
		} else {
			doIndexableDiscovery(request);
		}
	}

	private void doAtomClockDiscovery(AtomDiscoveryRequest request) throws DiscoveryException {
		List<AID> atomIds = Lists.newArrayList();
		long start = SystemProfiler.getInstance().begin();
		try (Cursor cursor = this.atomsDatabase.openCursor(null, null)) {
			long position = request.getCursor().getPosition();

			DatabaseEntry search = new DatabaseEntry(Longs.toByteArray(position + 1));
			DatabaseEntry data = new DatabaseEntry();
			OperationStatus status = cursor.getSearchKeyRange(search, data, LockMode.DEFAULT);

			while (status == OperationStatus.SUCCESS) {
				PreparedAtom preparedAtom = new PreparedAtom(data.getData());
				if (isCandidate(request, preparedAtom)) {
					position = Longs.fromByteArray(search.getData());
					atomIds.add(preparedAtom.getAtomID());
					if (atomIds.size() >= request.getLimit()) {
						break;
					}
				}
				status = cursor.getNext(search, data, LockMode.DEFAULT);
			}

			if (position != request.getCursor().getPosition()) {
				request.getCursor().setNext(new DiscoveryCursor(position));
			}
		} catch (IOException ioex) {
			throw new DiscoveryException(ioex);
		} finally {
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:DISCOVER:SYNC", start);
		}

		request.setInventory(atomIds);
	}

	private void doIndexableDiscovery(AtomDiscoveryRequest request) throws DiscoveryException
	{
		long start = SystemProfiler.getInstance().begin();
		List<AID> atomIds = new ArrayList<>();

		try
		{
			long position = request.getCursor().getPosition();

			SecondaryCursor cursor = null;

			try
			{
				// FIXME MPS: This really needs a closer look.
				// Query semantics have changed, because of indexing, and this will probably need
				// to be reflected in clients that are using this interface.
				DatabaseEntry data = new DatabaseEntry();
				DatabaseEntry pKey = new DatabaseEntry();

				final DatabaseEntry search;
				if (!request.getUID().isZero()) {
					search = new DatabaseEntry(IDType.toByteArray(IDType.UID, request.getUID()));
					cursor = this.duplicatedIndexables.openCursor(null, null);
				} else if (!request.getDestination().isZero()) {
					search = new DatabaseEntry(IDType.toByteArray(IDType.DESTINATION, request.getDestination()));
					cursor = this.duplicatedIndexables.openCursor(null, null);
				} else if (!request.getAID().isZero()) {
					search = new DatabaseEntry(IDType.toByteArray(IDType.ATOM, request.getAID()));
					cursor = this.uniqueIndexables.openCursor(null, null);
				} else {
					throw new IllegalStateException("Unspecified indexable");
				}

				OperationStatus status = cursor.getSearchKey(search, pKey, data, LockMode.DEFAULT);

				while (status == OperationStatus.SUCCESS)
				{
					PreparedAtom preparedAtom = new PreparedAtom(data.getData());

					if (isCandidate(request, preparedAtom)) {
						long clock = Longs.fromByteArray(pKey.getData());
						if (clock > position) {
							position = clock;
							atomIds.add(preparedAtom.getAtomID());
							if (atomIds.size() >= request.getLimit()) {
								break;
							}
						}
					}

					status = cursor.getNextDup(search, pKey, data, LockMode.DEFAULT);
				}
			} finally {
				if (cursor != null) {
					cursor.close();
				}
			}

			if (position != request.getCursor().getPosition()) {
				request.getCursor().setNext(new DiscoveryCursor(position));
			}
		}
		catch (IOException ioex)
		{
			throw new DiscoveryException(ioex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_STORE:DISCOVER:QUERY", start);
		}

		request.setInventory(atomIds);
	}

	@Override
	public void fetch(AtomDiscoveryRequest request) throws DiscoveryException
	{
		long start = SystemProfiler.getInstance().begin();

		try
		{
			request.setDelivered(getAtoms(request.getInventory()));
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


	private boolean isCandidate(AtomDiscoveryRequest request, PreparedAtom preparedAtom)
 	{
 		try
 		{
 			if (request.getParticle() != null) {
 				String particleClassId = Modules.get(Serialization.class).getIdForClass(request.getParticle());
 				if ((particleClassId == null) || preparedAtom.contains(IDType.PARTICLE_CLASS, SerializationUtils.stringToNumericID(particleClassId)) == false) {
	 				return false;
 				}
			}

			if (request.getUID() != null && !request.getUID().isZero() && !preparedAtom.contains(IDType.UID, request.getUID())) {
 				return false;
			}

			if (request.getTimestamp(Timestamps.FROM) > 0 && request.getTimestamp(Timestamps.TO) > 0) {
				if (preparedAtom.getTimestamp() < request.getTimestamp(Timestamps.FROM) || preparedAtom.getTimestamp() > request.getTimestamp(Timestamps.TO)) {
	 				return false;
				}
			}

			if (request.getShards() != null && request.getShards().intersects(preparedAtom.getShards()) == false) {
	 			return false;
			}

			if (request.getDestination() != null && !request.getDestination().isZero() && !preparedAtom.getDestinations().contains(request.getDestination())) {
	 			return false;
			}

 			return true;
 		}
 		catch (Exception ex)
 		{
			atomsLog.error("Could not determine candidacy for Atom:"+preparedAtom.getAtomID(), ex);
 			return false;
 		}
 	}

	// LEDGER CURSOR HANDLING //
	public LedgerCursor search(Type type, LedgerIndex indexable, LedgerSearchMode mode) throws DatabaseException
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
		catch(Exception ex)
		{
			throw new DatabaseException(ex);
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
