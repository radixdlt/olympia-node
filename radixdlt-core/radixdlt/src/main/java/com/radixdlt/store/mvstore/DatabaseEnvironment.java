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

package com.radixdlt.store.mvstore;

import com.google.inject.Inject;
import com.radixdlt.properties.RuntimeProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.h2.engine.IsolationLevel;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.tx.Transaction;
import org.h2.mvstore.tx.TransactionStore;

import java.io.File;
import java.util.Optional;
import java.util.function.Function;

public final class DatabaseEnvironment {
	public static final ByteArrayDataType BYTE_ARRAY_TYPE = new ByteArrayDataType();
	private static final Logger LOG = LogManager.getLogger();
	private static final long AVAILABLE_MEMORY = Runtime.getRuntime().maxMemory();
	private static final long MAX_CACHE_LIMIT = Math.max(50000000L, (long) (AVAILABLE_MEMORY * 0.1));
	private static final long MIN_CACHE_LIMIT = (long) (AVAILABLE_MEMORY * 0.25);
	private static final long DESIRED_CACHE_SIZE = (long) (AVAILABLE_MEMORY * 0.125);

	private final String dbName;
	private final int cacheSizeMB;
	private MVStore mvStore;
	private TransactionStore transactionStore;

	@Inject
	public DatabaseEnvironment(RuntimeProperties properties) {
		File dbHomeDir = new File(properties.get("db.location", ".//RADIXDB"));
		if (dbHomeDir.mkdir()) {
			LOG.info("DB working directory created");
		} else {
			LOG.info("DB working directory not created, assuming it already exists");
		}
		dbName = new File(dbHomeDir, "store.db").toString();
		cacheSizeMB = calculateCacheSize(properties);
	}

	public void start() {
		mvStore = new MVStore.Builder()
			.fileName(dbName)
			.cacheSize(cacheSizeMB)
			.autoCommitDisabled()
			.compress()
			.open();

		transactionStore = new TransactionStore(mvStore, new ByteArrayDataType(), 0);
		transactionStore.init();
	}

	public void stop() {
		transactionStore.close();
		mvStore.close();
	}

	public Transaction startTransaction() {
		var transaction = transactionStore.begin();
		transaction.setIsolationLevel(IsolationLevel.READ_COMMITTED);
		return transaction;
	}

	public <T> Optional<T> inTransaction(Function<Transaction, Optional<T>> action) {
		var transaction = transactionStore.begin();
		try {
			transaction.setIsolationLevel(IsolationLevel.READ_COMMITTED);
			Optional<T> result = action.apply(transaction);
			result.ifPresentOrElse(__ -> transaction.commit(), transaction::rollback);

			return result;
		} catch (Exception e) {
			transaction.rollback();
			return Optional.empty();
		}
	}

	private int calculateCacheSize(RuntimeProperties properties) {
		long minCacheSize = properties.get("db.cache_size.min", MAX_CACHE_LIMIT);
		long maxCacheSize = properties.get("db.cache_size.max", MIN_CACHE_LIMIT);
		long cacheSize = properties.get("db.cache_size", DESIRED_CACHE_SIZE);

		return (int) (Math.min(Math.max(cacheSize, minCacheSize), maxCacheSize) / 1_000_000);
	}
}
