/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix.utils;

import com.radixdlt.utils.Longs;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.radix.common.executors.Executor;
import org.radix.common.executors.ScheduledExecutable;
import org.radix.database.DatabaseEnvironment;
import org.radix.database.exceptions.DatabaseException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
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

// TODO Remove this horrible singleton with the particularly nasty set/unset/init lifecycle management.
public final class SystemMetaData
{
	private static final Logger log = Logging.getLogger();

	private static SystemMetaData instance;

	public static void set(SystemMetaData instance) {
		if (SystemMetaData.instance != null) {
			throw new IllegalStateException("metadata instance is already initialised");
		}
		SystemMetaData.instance = instance;
	}

	public static void clear() {
		if (instance != null) {
			instance.stop();
			instance = null;
		}
	}

	public static void init(DatabaseEnvironment dbEnv) {
		if (instance != null) {
			throw new IllegalStateException("metadata instance is already initialised");
		}
		instance = new SystemMetaData(dbEnv);
		instance.start();
	}

	public static void ifPresent(Consumer<SystemMetaData> consumer) {
		getInstanceOptional().ifPresent(consumer);
	}

	public static Optional<SystemMetaData> getInstanceOptional() {
		return Optional.ofNullable(instance);
	}

	public static SystemMetaData getInstance() {
		if (instance == null) {
			throw new RuntimeException("metadata instance has not been initialised");
		}

		return instance;
	}

	private final DatabaseEnvironment dbEnv;
	private Map<String, Object> systemMetaData = new ConcurrentHashMap<>();
	private Database systemMetaDataDB = null;
	private Future<?> flush;

	public SystemMetaData(DatabaseEnvironment dbEnv)
	{
		super();
		this.dbEnv = Objects.requireNonNull(dbEnv);
	}

	public void start() {
		DatabaseConfig config = new DatabaseConfig();
		config.setAllowCreate(true);

		this.systemMetaDataDB = this.dbEnv.getEnvironment().openDatabase(null, "system_meta_data", config);


		try
		{
			load();
		}
		catch (DatabaseException e)
		{
			throw new RuntimeException("while opening database", e);
		}

		ScheduledExecutable flushExecutable = new ScheduledExecutable(1, 1, TimeUnit.SECONDS) {
			@Override
			public void execute() {
				try {
					flush();
				} catch (DatabaseException e) {
					log.error(e.getMessage(), e);
				}
			}
		};
		this.flush = flushExecutable.getFuture();
		Executor.getInstance().scheduleWithFixedDelay(flushExecutable);
	}

	public void reset() {
		Transaction transaction = null;
		try
		{
			transaction = this.dbEnv.getEnvironment().beginTransaction(null, new TransactionConfig().setReadUncommitted(true));
			this.dbEnv.getEnvironment().truncateDatabase(transaction, "system_meta_data", false);
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

			throw new RuntimeException("while resetting database", ex);
		}
	}

	public void stop() {
		if (this.flush != null) {
			this.flush.cancel(false);
		}


		systemMetaDataDB.close();
	}

	public synchronized void flush() throws DatabaseException
	{
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
			throw new DatabaseException("While flushing system meta data", e);
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
			throw new DatabaseException("While loading system meta data", e);
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
