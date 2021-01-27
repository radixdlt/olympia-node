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
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.tx.Transaction;
import org.h2.mvstore.tx.TransactionStore;

import java.io.File;
import java.util.Optional;
import java.util.function.Function;

public final class DatabaseEnvironment {
	public static final ByteArrayDataType BYTE_ARRAY_TYPE = new ByteArrayDataType();
	private static final Logger LOG = LogManager.getLogger();

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
		cacheSizeMB = (int)(calculateCacheSize(properties) /1_000_000);
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

	private long calculateCacheSize(RuntimeProperties properties) {
		long minCacheSize = properties.get("db.cache_size.min", Math.max(50000000, (long) (Runtime.getRuntime().maxMemory() * 0.1)));
		long maxCacheSize = properties.get("db.cache_size.max", (long) (Runtime.getRuntime().maxMemory() * 0.25));
		long cacheSize = properties.get("db.cache_size", (long) (Runtime.getRuntime().maxMemory() * 0.125));
		cacheSize = Math.max(cacheSize, minCacheSize);
		cacheSize = Math.min(cacheSize, maxCacheSize);
		return cacheSize;
	}
}
