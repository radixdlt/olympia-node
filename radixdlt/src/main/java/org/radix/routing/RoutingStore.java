package org.radix.routing;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.radix.collections.WireableSet;
import com.radixdlt.common.EUID;
import org.radix.database.DatabaseEnvironment;
import org.radix.database.DBAction;
import org.radix.database.DatabaseStore;
import org.radix.database.exceptions.DatabaseException;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;
import org.radix.modules.exceptions.ModuleResetException;
import org.radix.modules.exceptions.ModuleStartException;
import com.radixdlt.serialization.DsonJavaType;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;

import com.google.common.primitives.Ints;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

public class RoutingStore extends DatabaseStore
{
	private Database routingPeriodNIDDB;

	public RoutingStore()
	{
		super();
	}

	@Override
	public void start_impl() throws ModuleException
	{
		DatabaseConfig config = new DatabaseConfig();
		config.setAllowCreate(true);

		try
		{
			this.routingPeriodNIDDB = Modules.get(DatabaseEnvironment.class).getEnvironment().openDatabase(null, "nids", config);
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
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "nids", false);
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

		this.routingPeriodNIDDB.close();
	}

	@Override
	public void build() throws DatabaseException { /* Not used */ }

	@Override
	public void maintenence() throws DatabaseException { /* Not used */ }

	@Override
	public void integrity() throws DatabaseException
	{
		// TODO needs to be implemented for RoutingStore?
	}

	@Override
	public void flush() throws DatabaseException  { /* Not used */ }

	@Override
	public String getName() { return "Routing Store"; }

	public Set<EUID> getNIDs(int period) throws DatabaseException
	{
		try
        {
			DatabaseEntry key = new DatabaseEntry(Ints.toByteArray(period));
			DatabaseEntry value = new DatabaseEntry();

			if (this.routingPeriodNIDDB.get(null, key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
				DsonJavaType type = Modules.get(Serialization.class).dsonCollectionType(WireableSet.class, EUID.class);
				return Modules.get(Serialization.class).fromDson(value.getData(), type);
			}
		}
		catch (Exception e)
		{
			throw new DatabaseException(e);
		}

		return new HashSet<EUID>();
	}

/*	public DBAction storeNIDs(long timestamp, Collection<EUID> NIDs) throws DatabaseException
	{
		return storeNIDS(Modules.get(Universe.class).toPlanck(timestamp, Offset.PREVIOUS), NIDs);
	}*/

	public DBAction storeNIDs(int period, Collection<EUID> NIDs) throws DatabaseException
	{
		try
        {
			DatabaseEntry key = new DatabaseEntry(Ints.toByteArray(period));
			byte[] bytes = Modules.get(Serialization.class).toDson(NIDs, Output.WIRE);
			DatabaseEntry value = new DatabaseEntry(bytes);

			if (this.routingPeriodNIDDB.put(null, key, value) != OperationStatus.SUCCESS)
				throw new DatabaseException("Failed to store routing NIDs for period "+Ints.fromByteArray(key.getData()));

			return new DBAction(DBAction.STORE, null, true);
		}
		catch (DatabaseException dbex)
		{
			throw dbex;
		}
		catch (Exception e)
		{
			throw new DatabaseException(e);
		}
	}
}

