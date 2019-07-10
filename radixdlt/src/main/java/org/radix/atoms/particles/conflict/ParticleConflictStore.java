package org.radix.atoms.particles.conflict;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.radix.atoms.Atom;
import com.radixdlt.common.EUID;
import org.radix.database.DatabaseEnvironment;
import org.radix.database.DBAction;
import org.radix.database.DatabaseStore;
import org.radix.database.exceptions.DatabaseException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;
import org.radix.modules.exceptions.ModuleResetException;
import org.radix.modules.exceptions.ModuleStartException;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import org.radix.state.State;
import org.radix.utils.SystemProfiler;
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
import com.sleepycat.je.SecondaryKeyCreator;
import com.sleepycat.je.SecondaryMultiKeyCreator;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

public class ParticleConflictStore extends DatabaseStore
{
	private static final Logger log = Logging.getLogger ();

	private Database conflictsDB = null;
	private SecondaryDatabase conflictParticlesIndex = null;
	private SecondaryDatabase conflictAtomsIndex = null;
	private Map<Long, ParticleConflict> processing = new ConcurrentHashMap<Long, ParticleConflict>();

	private class ConflictSecondayMultiKeyCreator implements SecondaryMultiKeyCreator
	{
		@Override
		public void createSecondaryKeys(SecondaryDatabase database, DatabaseEntry key, DatabaseEntry value, Set<DatabaseEntry> secondaries)
		{
			ParticleConflict conflict = processing.get(Longs.fromByteArray(key.getData()));

			if (database == conflictAtomsIndex)
			{
				for (Atom atom : conflict.getAtoms())
					secondaries.add(new DatabaseEntry(atom.getAID().getBytes()));
			}
		}
	}

	private class ConflictSecondayKeyCreator implements SecondaryKeyCreator
	{
		@Override
		public boolean createSecondaryKey(SecondaryDatabase database, DatabaseEntry key, DatabaseEntry value, DatabaseEntry secondary)
		{
			ParticleConflict conflict = processing.get(Longs.fromByteArray(key.getData()));

			if (database == conflictParticlesIndex)
			{
				secondary.setData(conflict.getSpunParticle().getParticle().getHID().toByteArray());
				return true;
			}

			return false;
		}
	}

	public ParticleConflictStore() { super(); }

	@Override
	public void start_impl() throws ModuleException
	{
		DatabaseConfig primaryConfig = new DatabaseConfig();
		primaryConfig.setAllowCreate(true);
		primaryConfig.setTransactional(true);

		SecondaryConfig secondaryMultiKeyIndexConfig = new SecondaryConfig();
		secondaryMultiKeyIndexConfig.setAllowCreate(true);
		secondaryMultiKeyIndexConfig.setTransactional(true);
		secondaryMultiKeyIndexConfig.setSortedDuplicates(true);
		secondaryMultiKeyIndexConfig.setMultiKeyCreator(new ConflictSecondayMultiKeyCreator());

		SecondaryConfig secondaryKeyIndexConfig = new SecondaryConfig();
		secondaryKeyIndexConfig.setAllowCreate(true);
		secondaryKeyIndexConfig.setTransactional(true);
		secondaryKeyIndexConfig.setSortedDuplicates(true);
		secondaryKeyIndexConfig.setKeyCreator(new ConflictSecondayKeyCreator());

		try
		{
			conflictsDB = Modules.get(DatabaseEnvironment.class).getEnvironment().openDatabase(null, "conflicts", primaryConfig);
			conflictParticlesIndex = Modules.get(DatabaseEnvironment.class).getEnvironment().openSecondaryDatabase(null, "conflict_particles_index", conflictsDB, secondaryMultiKeyIndexConfig);
			conflictAtomsIndex = Modules.get(DatabaseEnvironment.class).getEnvironment().openSecondaryDatabase(null, "conflict_atoms_index", conflictsDB, secondaryKeyIndexConfig);
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
			transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "conflicts", false);
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "conflict_atoms_index", false);
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "conflict_particles_index", false);
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
	}

	@Override
	public void stop_impl() throws ModuleException
	{
		super.stop_impl();

		conflictAtomsIndex.close();
		conflictAtomsIndex = null;
		conflictParticlesIndex.close();
		conflictParticlesIndex = null;
		conflictsDB.close();
		conflictsDB = null;
	}

	@Override
	public void build() throws DatabaseException { }

	@Override
	public void maintenence() throws DatabaseException { }

	@Override
	public void integrity() throws DatabaseException { }

	@Override
	public void flush() throws DatabaseException
	{
	}

	@Override
	public String getName() { return "Particle Conflict Store"; }

	public boolean hasConflict(EUID id) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		try
        {
			DatabaseEntry key = new DatabaseEntry(id.toByteArray());

			if (conflictsDB.get(null, key, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
				return true;

			key.setData(id.toByteArray());
			if (conflictParticlesIndex.get(null, key, null, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
				return true;

			if (conflictAtomsIndex.get(null, key, null, null, LockMode.DEFAULT) == OperationStatus.SUCCESS)
				return true;
		}
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("PARTICLE_CONFLICT_STORE:HAS_CONFLICT", start);
		}

		return false;
	}


	public ParticleConflict getConflict(EUID id) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		try
        {
			DatabaseEntry key = new DatabaseEntry(id.toByteArray());
			DatabaseEntry data = new DatabaseEntry();

			if (conflictsDB.get(null, key, data, LockMode.DEFAULT) == OperationStatus.SUCCESS)
				return Modules.get(Serialization.class).fromDson(data.getData(), ParticleConflict.class);

			key.setData(id.toByteArray());
			if (conflictParticlesIndex.get(null, key, null, data, LockMode.DEFAULT) == OperationStatus.SUCCESS)
				return Modules.get(Serialization.class).fromDson(data.getData(), ParticleConflict.class);

			if (conflictAtomsIndex.get(null, key, null, data, LockMode.DEFAULT) == OperationStatus.SUCCESS)
				return Modules.get(Serialization.class).fromDson(data.getData(), ParticleConflict.class);
		}
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("ATOM_CONFLICT_STORE:GET_CONFLICT", start);
		}

		return null;
	}

	public List<ParticleConflict> getConflicts(Collection<EUID> ids) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();
        List<ParticleConflict> conflicts = new ArrayList<ParticleConflict>();

		try
        {
			DatabaseEntry key = new DatabaseEntry();
			DatabaseEntry data = new DatabaseEntry();

			for (EUID id : ids)
			{
				key.setData(id.toByteArray());
				if (conflictsDB.get(null, key, data, LockMode.DEFAULT) == OperationStatus.SUCCESS)
					conflicts.add(Modules.get(Serialization.class).fromDson(data.getData(), ParticleConflict.class));
			}
		}
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("PARTICLE_CONFLICT_STORE:GET_CONFLICTS", start);
		}

		return conflicts;
	}

	public Set<EUID> getConflicts(EUID id) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		Set<EUID> conflicts = new LinkedHashSet<EUID>();

		try
        {
			DatabaseEntry 	search = new DatabaseEntry();
			DatabaseEntry 	key = new DatabaseEntry(id.toByteArray());
			DatabaseEntry 	data = new DatabaseEntry();
			SecondaryCursor cursor = null;

			if (conflictsDB.get(null, key, data, LockMode.DEFAULT) == OperationStatus.SUCCESS)
				conflicts.add(new EUID(key.getData()));

			try
			{
				cursor = conflictAtomsIndex.openCursor(null, null);
				OperationStatus status = OperationStatus.NOTFOUND;
				search.setData(id.toByteArray());
				status = cursor.getSearchKey(search, key, data, LockMode.DEFAULT);

				while (status == OperationStatus.SUCCESS)
				{
					conflicts.add(new EUID(key.getData()));
					status = cursor.getNext(search, key, data, LockMode.DEFAULT);
				}
			}
			finally
			{
				if (cursor != null)
					cursor.close();
			}

			try
			{
				cursor = conflictParticlesIndex.openCursor(null, null);
				OperationStatus status = OperationStatus.NOTFOUND;
				search.setData(id.toByteArray());
				status = cursor.getSearchKey(search, key, data, LockMode.DEFAULT);

				while (status == OperationStatus.SUCCESS)
				{
					conflicts.add(new EUID(key.getData()));
					status = cursor.getNext(search, key, data, LockMode.DEFAULT);
				}
			}
			finally
			{
				if (cursor != null)
					cursor.close();
			}
		}
		catch (Exception ex)
		{
			throw new DatabaseException(ex);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("PARTICLE_CONFLICT_STORE:GET_CONFLICTS:IDS", start);
		}

		return conflicts;
	}

	public DBAction storeConflict(final ParticleConflict conflict) throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		Transaction transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, new TransactionConfig().setReadUncommitted(true));

		try
        {
			processing.put(conflict.getUID().getLow(), conflict);
			byte[] bytes = Modules.get(Serialization.class).toDson(conflict, Output.PERSIST);
			conflictsDB.put(transaction, new DatabaseEntry(conflict.getUID().toByteArray()), new DatabaseEntry(bytes));

			transaction.commit();

			return new DBAction(State.STORED.getName().toLowerCase(), conflict, true);
		}
		catch (Exception ex)
		{
			transaction.abort();

			throw new DatabaseException(ex);
		}
		finally
		{
			processing.remove(conflict.getUID().getLow());

			SystemProfiler.getInstance().incrementFrom("PARTICLE_CONFLICT_STORE:STORE_CONFLICT", start);
		}
	}


}