package org.radix.utils;

import com.radixdlt.utils.Longs;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.radix.common.executors.ScheduledExecutable;
import org.radix.database.DatabaseEnvironment;
import org.radix.database.DatabaseStore;
import org.radix.database.exceptions.DatabaseException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;
import org.radix.modules.exceptions.ModuleResetException;
import org.radix.modules.exceptions.ModuleStartException;
import com.radixdlt.utils.Bytes;

import com.radixdlt.utils.RadixConstants;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

public final class SystemMetaData extends DatabaseStore
{
	private static final Logger log = Logging.getLogger();

	private Map<String, Object> systemMetaData = new ConcurrentHashMap<>();
	private Database systemMetaDataDB = null;
	private Future<?> flush;

	public SystemMetaData()
	{
		super();
	}

	@Override
	public void build() throws DatabaseException { }

	@Override
	public void maintenence() throws DatabaseException { }

	@Override
	public void integrity() throws DatabaseException { }

	@Override
	public void start_impl() throws ModuleException
	{
		DatabaseConfig config = new DatabaseConfig();
		config.setAllowCreate(true);

		this.systemMetaDataDB = Modules.get(DatabaseEnvironment.class).getEnvironment().openDatabase(null, "system_meta_data", config);

		super.start_impl();

		try
		{
			load();
		}
		catch (DatabaseException e)
		{
			throw new ModuleStartException(e, this);
		}

		this.flush = scheduleWithFixedDelay(new ScheduledExecutable(1, 1, TimeUnit.SECONDS)
		{
			@Override
			public void execute()
			{
				try
				{
					flush();
				}
				catch (DatabaseException e)
				{
					log.error(e.getMessage(), e);
				}
			}
		});
	}

	@Override
	public void reset_impl() throws ModuleException
	{
		Transaction transaction = null;

		try
		{
			transaction = Modules.get(DatabaseEnvironment.class).getEnvironment().beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
			Modules.get(DatabaseEnvironment.class).getEnvironment().truncateDatabase(transaction, "system_meta_data", false);
			transaction.commit();
			this.systemMetaData.clear();
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
		if (this.flush != null) {
			this.flush.cancel(false);
		}

		super.stop_impl();

		systemMetaDataDB.close();
	}

	@Override
	public String getName() { return "System Meta Data DBPlugin"; }

	// DBPLUGIN BUILDER //
	@Override
	public synchronized void flush() throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		try
        {
			for (Map.Entry<String, Object> e : this.systemMetaData.entrySet())
			{
				DatabaseEntry key = new DatabaseEntry(e.getKey().getBytes(RadixConstants.STANDARD_CHARSET));
				Object value = e.getValue();
				Class<?> valueClass = value.getClass();
				final byte[] bytes;
				if (valueClass.equals(String.class)) {
					String stringValue = (String) value;
					byte[] stringBytes = stringValue.getBytes(RadixConstants.STANDARD_CHARSET);
					bytes = new byte[1 + stringBytes.length];
					bytes[0] = 'S';
					System.arraycopy(stringBytes, 0, bytes, 1, stringBytes.length);
				} else if (valueClass.equals(Long.class)) {
					Long longValue = (Long) value;
					bytes = new byte[1 + Long.BYTES];
					bytes[0] = 'L';
					Longs.copyTo(longValue.longValue(), bytes, 1);
				} else if (valueClass.equals(byte[].class)) {
					byte[] bytesValue = (byte[]) value;
					bytes = new byte[1 + bytesValue.length];
					bytes[0] = 'B';
					System.arraycopy(bytesValue, 0, bytes, 1, bytesValue.length);
				} else {
					throw new IllegalArgumentException("Unknown value type: " + valueClass.getName());
				}

				this.systemMetaDataDB.put(null, key, new DatabaseEntry(bytes));
			}
		}
		catch (Exception e)
		{
			throw new DatabaseException(e);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("SYSTEMMETRICS_FLUSH", start);
		}
	}

	// SYSTEM METRICS //
	public boolean has(String name)
	{
		return this.systemMetaData.containsKey(name);
	}

	public String get(String name, String option)
	{
		Object value = this.systemMetaData.get(name);

		if (value == null)
			return option;

		return asString(value);
	}

	public long get(String name, long option)
	{
		Object value = this.systemMetaData.get(name);

		if (value == null)
			return option;

		return asLong(value);
	}

	public byte[] get(String name, byte[] option)
	{
		Object value = this.systemMetaData.get(name);

		if (value == null)
			return option;

		return asBytes(value);
	}


	public long increment(String name)
	{
		return (long) this.systemMetaData.compute(name, (k, v) -> {
			long value = (long) this.systemMetaData.getOrDefault(k, 0l) + 1;
			return value;
		});
	}

	public long increment(String name, long increment)
	{
		return (long) this.systemMetaData.compute(name, (k, v) -> {
			long value = (long) this.systemMetaData.getOrDefault(k, 0l) + increment;
			return value;
		});
	}

	public long decrement(String name)
	{
		return (long) this.systemMetaData.compute(name, (k, v) -> {
			long value = (long) this.systemMetaData.getOrDefault(k, 0l) - 1;
			return value;
		});
	}

	public long decrement(String name, long decrement)
	{
		return (long) this.systemMetaData.compute(name, (k, v) -> {
			long value = (long) this.systemMetaData.getOrDefault(k, 0l) - decrement;
			return value;
		});
	}

	public void put(String name, String value)
	{
		this.systemMetaData.put(name, value);
	}

	public void put(String name, long value)
	{
		this.systemMetaData.put(name, value);
	}

	public void put(String name, byte[] value)
	{
		// Take a defensive copy
		this.systemMetaData.put(name, value.clone());
	}

	/**
	 * Gets the meta data from the DB
	 *
	 * @throws DatabaseException
	 */
	private void load() throws DatabaseException
	{
		long start = SystemProfiler.getInstance().begin();

		try (Cursor cursor = this.systemMetaDataDB.openCursor(null, null))
        {
			DatabaseEntry key = new DatabaseEntry();
			DatabaseEntry value = new DatabaseEntry();

			this.systemMetaData.clear();

			while (cursor.getNext(key, value, LockMode.DEFAULT) == OperationStatus.SUCCESS)
			{
				String keyString = Bytes.toString(key.getData()).toLowerCase();
				byte[] bytes = value.getData();
				byte[] newBytes = new byte[bytes.length - 1];
				System.arraycopy(bytes, 1, newBytes, 0, newBytes.length);
				switch (bytes[0]) {
				case 'S':
					this.systemMetaData.put(keyString, Bytes.toString(newBytes));
					break;
				case 'L':
					this.systemMetaData.put(keyString, Longs.fromByteArray(newBytes));
					break;
				case 'B':
					this.systemMetaData.put(keyString, newBytes);
					break;
				default:
					throw new IllegalArgumentException("Unknown type byte: " + bytes[0]);
				}
			}
		}
		catch (Exception e)
		{
			throw new DatabaseException(e);
		}
		finally
		{
			SystemProfiler.getInstance().incrementFrom("SYSTEMMETRICS_GET_METRICS", start);
		}
	}

	private static String asString(Object value)
	{
		return (String) value;
	}

	private static long asLong(Object value)
	{
		return ((Long) value).longValue();
	}

	private static byte[] asBytes(Object value)
	{
		return (byte[]) value;
	}
}
