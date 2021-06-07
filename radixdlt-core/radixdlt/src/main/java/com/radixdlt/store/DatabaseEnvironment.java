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

package com.radixdlt.store;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.sleepycat.je.CacheMode;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

import java.io.File;
import java.text.StringCharacterIterator;
import java.util.concurrent.TimeUnit;

import static com.sleepycat.je.Durability.COMMIT_NO_SYNC;
import static com.sleepycat.je.EnvironmentConfig.CHECKPOINTER_BYTES_INTERVAL;
import static com.sleepycat.je.EnvironmentConfig.ENV_RUN_CHECKPOINTER;
import static com.sleepycat.je.EnvironmentConfig.ENV_RUN_CLEANER;
import static com.sleepycat.je.EnvironmentConfig.ENV_RUN_EVICTOR;
import static com.sleepycat.je.EnvironmentConfig.ENV_RUN_VERIFIER;
import static com.sleepycat.je.EnvironmentConfig.LOG_FILE_CACHE_SIZE;
import static com.sleepycat.je.EnvironmentConfig.LOG_FILE_MAX;
import static com.sleepycat.je.EnvironmentConfig.TREE_MAX_EMBEDDED_LN;

public final class DatabaseEnvironment {
	private static final Logger log = LogManager.getLogger();

	private Environment environment;

	@Inject
	public DatabaseEnvironment(
		@DatabaseLocation String databaseLocation,
		@DatabaseCacheSize long cacheSize
	) {
		var dbHome = new File(databaseLocation);
		dbHome.mkdir();

		System.setProperty("je.disable.java.adler32", "true");

		EnvironmentConfig environmentConfig = new EnvironmentConfig();
		environmentConfig.setTransactional(true);
		environmentConfig.setAllowCreate(true);
		environmentConfig.setLockTimeout(30, TimeUnit.SECONDS);
		environmentConfig.setDurability(COMMIT_NO_SYNC);
		environmentConfig.setConfigParam(LOG_FILE_CACHE_SIZE, "256");
		environmentConfig.setConfigParam(ENV_RUN_CHECKPOINTER, "true");
		environmentConfig.setConfigParam(ENV_RUN_CLEANER, "true");
		environmentConfig.setConfigParam(ENV_RUN_EVICTOR, "true");
		environmentConfig.setConfigParam(ENV_RUN_VERIFIER, "false");
		environmentConfig.setConfigParam(TREE_MAX_EMBEDDED_LN, "0");
		environmentConfig.setCacheSize(cacheSize);
		environmentConfig.setCacheMode(CacheMode.EVICT_LN);

		this.environment = new Environment(dbHome, environmentConfig);

		log.info("DB cache size set to {} ({} bytes)", toHumanReadable(cacheSize), cacheSize);
	}

	public void stop() {
		try {
			this.environment.close();
		} catch (DatabaseException e) {
			log.error("Error while closing database. Possible DB corruption.");
		}
		this.environment = null;
	}

	public Environment getEnvironment() {
		if (this.environment == null) {
			throw new IllegalStateException("environment is not started");
		}

		return this.environment;
	}

	private static String toHumanReadable(long bytes) {
		var absB = bytes == Long.MIN_VALUE
					? Long.MAX_VALUE
					: Math.abs(bytes);

		if (absB < 1024) {
			return bytes + " B";
		}

		var value = absB;
		var ci = new StringCharacterIterator("KMGTPE");

		for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
			value >>= 10;
			ci.next();
		}

		value *= Long.signum(bytes);
		return String.format("%.1f %ciB", value / 1024.0, ci.current());
	}
}
