/*
 * (C) Copyright 2021 Radix DLT Ltd
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

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.tx.Transaction;
import org.h2.mvstore.tx.TransactionMap;
import org.h2.value.VersionedValue;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.radixdlt.counters.SystemCounters.CounterType;
import static com.radixdlt.store.mvstore.CommonCounterType.BYTES_READ;
import static com.radixdlt.store.mvstore.CommonCounterType.BYTES_WRITE;
import static com.radixdlt.store.mvstore.CommonCounterType.ELAPSED;
import static com.radixdlt.store.mvstore.CommonCounterType.TOTAL;
import static com.radixdlt.store.mvstore.DatabaseEnvironment.BYTE_ARRAY_TYPE;

public class MVStoreBase {
	protected static final Optional<Boolean> SUCCESS = Optional.of(true);
	protected static final Optional<Boolean> FAILURE = Optional.empty();

	private final Serialization serialization;
	private final DatabaseEnvironment dbEnv;
	private final SystemCounters systemCounters;
	private final String dbName;
	private final Map<CommonCounterType, CounterType> counterNames;

	public MVStoreBase(
		Serialization serialization,
		DatabaseEnvironment dbEnv,
		SystemCounters systemCounters,
		String dbName,
		Map<CommonCounterType, CounterType> counterNames
	) {
		this.serialization = serialization;
		this.dbEnv = dbEnv;
		this.systemCounters = systemCounters;
		this.dbName = dbName;
		this.counterNames = counterNames;
	}

	protected MVMap<byte[], VersionedValue> openMap(String name) {
		return dbEnv.openMap(name);
	}

	protected TransactionMap<byte[], byte[]> openMap(Transaction tx) {
		return tx.openMap(dbName, BYTE_ARRAY_TYPE, BYTE_ARRAY_TYPE);
	}

	protected TransactionMap<byte[], byte[]> openMap(String name, Transaction tx) {
		return tx.openMap(name, BYTE_ARRAY_TYPE, BYTE_ARRAY_TYPE);
	}

	protected <T> T withTime(Supplier<T> supplier) {
		final var start = System.nanoTime();
		try {
			return supplier.get();
		} finally {
			addTime(start);
		}
	}

	protected <T> T withTime(Supplier<T> supplier, CounterType detailTime, CounterType detailCounter) {
		final var start = System.nanoTime();
		try {
			return supplier.get();
		} finally {
			addTime(start, detailTime, detailCounter);
		}
	}

	protected <T> Optional<T> withTimeInTx(Function<Transaction, Optional<T>> action) {
		return withTime(() -> inTransaction(action));
	}

	protected <T> Optional<T> withTimeInTx(Function<Transaction, Optional<T>> action, CounterType detailTime, CounterType detailCounter) {
		return withTime(() -> inTransaction(action), detailTime, detailCounter);
	}

	protected <T> T fromDson(byte[] value, Class<T> clazz) throws DeserializeException {
		return serialization.fromDson(value, clazz);
	}

	protected <T> Optional<T> safeFromDson(byte[] value, Class<T> clazz) {
		try {
			return Optional.ofNullable(serialization.fromDson(value, clazz));
		} catch (DeserializeException e) {
			return Optional.empty();
		}
	}

	protected byte[] toDson(Object value) {
		return serialization.toDson(value, DsonOutput.Output.PERSIST);
	}

	protected byte[] toDson(Object value, DsonOutput.Output output) {
		return serialization.toDson(value, output);
	}

	protected <T> Optional<T> inTransaction(Function<Transaction, Optional<T>> action) {
		return dbEnv.inTransaction(action);
	}

	protected Transaction startTransaction() {
		return dbEnv.startTransaction();
	}

	protected void increment(CounterType counterType) {
		systemCounters.increment(counterType);
	}

	protected long addTime(long start) {
		final var elapsed = (System.nanoTime() - start + 500L) / 1000L;
		systemCounters.add(counterNames.get(ELAPSED), elapsed);
		systemCounters.increment(counterNames.get(TOTAL));
		return elapsed;
	}

	protected void addTime(long start, CounterType detailTime, CounterType detailCounter) {
		final var elapsed = addTime(start);

		systemCounters.add(detailTime, elapsed);
		systemCounters.increment(detailCounter);
	}

	protected void addBytesRead(int bytesRead) {
		systemCounters.add(counterNames.get(BYTES_READ), bytesRead);
	}

	protected void addBytesWrite(int bytesWrite) {
		systemCounters.add(counterNames.get(BYTES_WRITE), bytesWrite);
	}

	protected static <T> T sideEffect(T value, Runnable runnable) {
		runnable.run();
		return value;
	}
}
