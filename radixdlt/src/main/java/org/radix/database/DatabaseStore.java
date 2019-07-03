package org.radix.database;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.radix.database.exceptions.DatabaseException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Module;
import org.radix.modules.Modules;
import org.radix.modules.Service;
import org.radix.modules.exceptions.ModuleException;
import org.radix.modules.exceptions.ModuleStartException;
import org.radix.modules.exceptions.ModuleStopException;

public abstract class DatabaseStore extends Service //implements Flushable
{
	protected	static final Logger log = Logging.getLogger ();

	protected	int	buildPriority;

	public DatabaseStore() { this(50); }

	public DatabaseStore(int buildPriority)
	{
		super();

		this.buildPriority = buildPriority;
	}

	// SERVICE //
	@Override
	public List<Class<? extends Module>> getDependsOn()
	{
		List<Class<? extends Module>> dependencies = new ArrayList<Class<? extends Module>>();
		dependencies.add(DatabaseEnvironment.class);
		return Collections.unmodifiableList(dependencies);
	}

	@Override
	public void start_impl() throws ModuleException
	{
		try
		{
			if (System.getProperty("db.check_integrity", "1").equals("1"))
				integrity();

			if (Modules.get(DatabaseEnvironment.class).isRegistered(this) == false)
				Modules.get(DatabaseEnvironment.class).register(this);
		}
		catch (DatabaseException ex)
		{
			throw new ModuleStartException(ex, this);
		}
	}

	@Override
	public void stop_impl() throws ModuleException
	{
		try
		{
			flush();
		}
		catch (DatabaseException e)
		{
			throw new ModuleStopException(e, this);
		}

		if (Modules.get(DatabaseEnvironment.class).isRegistered(this) == true)
			Modules.get(DatabaseEnvironment.class).deregister(this);
	}

	// DBPLUGIN BUILDER //
	public int getBuildPriority() { return this.buildPriority; }

	public abstract void build() throws DatabaseException;

	public abstract void maintenence() throws DatabaseException;

	public abstract void integrity() throws DatabaseException;

	public abstract void flush() throws DatabaseException;

	// DEFAULT FUNCTIONS //
//	protected abstract Object get(DatabaseEntry entry, Class<?> type) throws DatabaseException;
}
