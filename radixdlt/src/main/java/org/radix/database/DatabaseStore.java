package org.radix.database;

import org.radix.database.exceptions.DatabaseException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleStartException;
import org.radix.modules.exceptions.ModuleStopException;

public abstract class DatabaseStore
{
	protected	static final Logger log = Logging.getLogger ();

	protected	int	buildPriority;

	public DatabaseStore() { this(50); }

	public DatabaseStore(int buildPriority)
	{
		super();

		this.buildPriority = buildPriority;
	}

	public void start_impl()
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
			throw new RuntimeException("Error while starting database");
		}
	}

	public void stop_impl()
	{
		try
		{
			flush();
		}
		catch (DatabaseException e)
		{
			throw new RuntimeException("while flushing database", e);
		}

		if (Modules.get(DatabaseEnvironment.class).isRegistered(this) == true)
			Modules.get(DatabaseEnvironment.class).deregister(this);
	}

	public abstract void reset_impl();

	public int getBuildPriority() { return this.buildPriority; }

	public abstract void build() throws DatabaseException;

	public abstract void maintenence() throws DatabaseException;

	public abstract void integrity() throws DatabaseException;

	public abstract void flush() throws DatabaseException;
}
