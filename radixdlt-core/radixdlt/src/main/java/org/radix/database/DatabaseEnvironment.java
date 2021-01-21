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

package org.radix.database;

import com.google.inject.Inject;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.store.Transaction;
import com.radixdlt.utils.RadixConstants;
import com.sleepycat.je.CacheMode;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

import org.bouncycastle.util.Arrays;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class DatabaseEnvironment {
	private final ReentrantLock lock = new ReentrantLock(true);
	private Database metaDatabase;
	private Environment environment = null;

	@Inject
	public DatabaseEnvironment(RuntimeProperties properties) {
		File dbhome = new File(properties.get("db.location", ".//RADIXDB"));
		dbhome.mkdir();

		System.setProperty("je.disable.java.adler32", "true");

		EnvironmentConfig environmentConfig = new EnvironmentConfig();
		environmentConfig.setTransactional(true);
		environmentConfig.setAllowCreate(true);
		environmentConfig.setLockTimeout(30, TimeUnit.SECONDS);
		environmentConfig.setDurability(Durability.COMMIT_NO_SYNC);
		environmentConfig.setConfigParam(EnvironmentConfig.LOG_FILE_MAX, "100000000");
		environmentConfig.setConfigParam(EnvironmentConfig.LOG_FILE_CACHE_SIZE, "256");
		environmentConfig.setConfigParam(EnvironmentConfig.ENV_RUN_CHECKPOINTER, "true");
		environmentConfig.setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, "true");
		environmentConfig.setConfigParam(EnvironmentConfig.ENV_RUN_EVICTOR, "true");
		environmentConfig.setConfigParam(EnvironmentConfig.ENV_RUN_VERIFIER, "false");
		environmentConfig.setConfigParam(EnvironmentConfig.TREE_MAX_EMBEDDED_LN, "0");

		long minCacheSize = properties.get("db.cache_size.min", Math.max(50000000, (long) (Runtime.getRuntime().maxMemory() * 0.1)));
		long maxCacheSize = properties.get("db.cache_size.max", (long) (Runtime.getRuntime().maxMemory() * 0.25));
		long cacheSize = properties.get("db.cache_size", (long) (Runtime.getRuntime().maxMemory() * 0.125));
		cacheSize = Math.max(cacheSize, minCacheSize);
		cacheSize = Math.min(cacheSize, maxCacheSize);

		environmentConfig.setCacheSize(cacheSize);
		environmentConfig.setCacheMode(CacheMode.EVICT_LN);

		this.environment = new Environment(dbhome, environmentConfig);

		DatabaseConfig primaryConfig = new DatabaseConfig();
		primaryConfig.setAllowCreate(true);
		primaryConfig.setTransactional(true);

		try {
			this.metaDatabase = this.environment.openDatabase(null, "environment.meta_data", primaryConfig);
		} catch (Exception ex) {
			throw new RuntimeException("while opening database", ex);
		}
	}

	public void stop() {
		this.metaDatabase.close();
		this.metaDatabase = null;

		this.environment.close();
		this.environment = null;
	}

	public void withLock(Runnable runnable) {
		this.lock.lock();
		try {
			runnable.run();
		} finally {
			this.lock.unlock();
		}
	}

	public Environment getEnvironment() {
		if (this.environment == null) {
			throw new IllegalStateException("environment is not started");
		}

		return this.environment;
	}

	public OperationStatus put(Transaction transaction, String resource, String key, byte[] value) {
		return this.put(transaction, resource, new DatabaseEntry(key.getBytes()), new DatabaseEntry(value));
	}

	public OperationStatus put(Transaction transaction, String resource, String key, DatabaseEntry value) {
		return this.put(transaction, resource, new DatabaseEntry(key.getBytes()), value);
	}

	public OperationStatus put(Transaction transaction, String resource, DatabaseEntry key, DatabaseEntry value) {
		if (resource == null || resource.length() == 0) {
			throw new IllegalArgumentException("Resource can not be null or empty");
		}

		if (key == null || key.getData() == null || key.getData().length == 0) {
			throw new IllegalArgumentException("Key can not be null or empty");
		}

		if (value == null || value.getData() == null || value.getData().length == 0) {
			throw new IllegalArgumentException("Value can not be null or empty");
		}

		// Create a key specific to the database //
		key.setData(Arrays.concatenate(resource.getBytes(RadixConstants.STANDARD_CHARSET), key.getData()));

		return this.metaDatabase.put((com.sleepycat.je.Transaction) transaction.unwrap(), key, value);
	}

	public byte[] get(String resource, String key) {
		DatabaseEntry value = new DatabaseEntry();

		if (this.get(resource, new DatabaseEntry(key.getBytes()), value) == OperationStatus.SUCCESS) {
			return value.getData();
		}

		return null;
	}

	public OperationStatus get(String resource, String key, DatabaseEntry value) {
		return this.get(resource, new DatabaseEntry(key.getBytes()), value);
	}

	public OperationStatus get(String resource, DatabaseEntry key, DatabaseEntry value) {
		if (resource == null || resource.length() == 0) {
			throw new IllegalArgumentException("Resource can not be null or empty");
		}

		if (key == null || key.getData() == null || key.getData().length == 0) {
			throw new IllegalArgumentException("Key can not be null or empty");
		}

		if (value == null) {
			throw new IllegalArgumentException("Value can not be null");
		}

		// Create a key specific to the database //
		key.setData(Arrays.concatenate(resource.getBytes(RadixConstants.STANDARD_CHARSET), key.getData()));

		return this.metaDatabase.get(null, key, value, LockMode.READ_UNCOMMITTED);
	}
}
