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
import org.h2.engine.IsolationLevel;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.tx.Transaction;
import org.h2.mvstore.tx.TransactionStore;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public final class DatabaseEnvironment {
	public static final ByteArrayDataType BYTE_ARRAY_TYPE =new ByteArrayDataType();

	private final ReentrantLock lock = new ReentrantLock(true);

	private final MVStore mvStore;
	private final TransactionStore transactionStore;
	private final MVMap<byte[], byte[]> metaDatabase;
	private final MVMap.Builder<byte[], byte[]> mapTemplate;

	@Inject
	public DatabaseEnvironment(RuntimeProperties properties) {
		File dbhome = new File(properties.get("db.location", ".//RADIXDB"));
		dbhome.mkdir();

		long minCacheSize = properties.get("db.cache_size.min", Math.max(50000000, (long) (Runtime.getRuntime().maxMemory() * 0.1)));
		long maxCacheSize = properties.get("db.cache_size.max", (long) (Runtime.getRuntime().maxMemory() * 0.25));
		long cacheSize = properties.get("db.cache_size", (long) (Runtime.getRuntime().maxMemory() * 0.125));
		cacheSize = Math.max(cacheSize, minCacheSize);
		cacheSize = Math.min(cacheSize, maxCacheSize);
		int cacheSizeMB = (int)(cacheSize/1_000_000);

		mvStore = new MVStore.Builder()
			.fileName(new File(dbhome, "store.db").toString())
			.cacheSize(cacheSizeMB)
			.autoCommitDisabled()
			.compress()
			.open();

		transactionStore = new TransactionStore(mvStore, new ByteArrayDataType(), 0);
		mapTemplate = new MVMap.Builder<byte[], byte[]>()
			.valueType(BYTE_ARRAY_TYPE)
			.keyType(BYTE_ARRAY_TYPE);

		try {
			//TODO: do we need it???
			this.metaDatabase = mvStore.openMap(
				"environment.meta_data",
				mapTemplate
			);
		} catch (Exception ex) {
			throw new RuntimeException("while opening database", ex);
		}
	}

	public void start() {
		this.transactionStore.init();
	}

	public void stop() {
		this.transactionStore.close();
	}

	public MVMap<byte[], byte[]> open(String name) {
		return mvStore.openMap(name, mapTemplate);
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
}
