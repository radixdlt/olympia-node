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

package com.radixdlt.client.store.berkeley;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.SpunParticle;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.store.ClientApiStore;
import com.radixdlt.client.store.ClientApiStoreException;
import com.radixdlt.client.store.TokenBalance;
import com.radixdlt.client.store.TokenDefinitionRecord;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.AtomsCommittedToLedger;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.OperationStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_BALANCE_BYTES_READ;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_BALANCE_BYTES_WRITE;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_BALANCE_READ;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_BALANCE_TOTAL;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_BALANCE_WRITE;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_PARTICLE_FLUSH_COUNT;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_PARTICLE_QUEUE_SIZE;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_TOKEN_BYTES_READ;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_TOKEN_BYTES_WRITE;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_TOKEN_READ;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_TOKEN_TOTAL;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_TOKEN_WRITE;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_APIDB_BALANCE_READ;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_APIDB_BALANCE_WRITE;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_APIDB_PARTICLE_FLUSH_TIME;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_APIDB_TOKEN_READ;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_APIDB_TOKEN_WRITE;
import static com.radixdlt.serialization.DsonOutput.Output;

public class BerkeleyClientApiStore implements ClientApiStore {
	private static final Logger log = LogManager.getLogger();

	private static final String EXECUTED_TRANSACTIONS_DB = "radix.executed_transactions_db";
	private static final String BALANCE_DB = "radix.balance_db";
	private static final String TOKEN_DEFINITION_DB = "radix.token_definition_db";
	private static final long DEFAULT_FLUSH_INTERVAL = 100L;

	private final DatabaseEnvironment dbEnv;
	private final BerkeleyLedgerEntryStore store;
	private final Serialization serialization;
	private final SystemCounters systemCounters;
	private final ScheduledEventDispatcher<ScheduledParticleFlush> scheduledFlushEventDispatcher;
	private final Observable<AtomsCommittedToLedger> ledgerCommitted;
	private final StackingCollector<SpunParticle> particleCollector = StackingCollector.create();
	private final AtomicLong inputCounter = new AtomicLong();
	private final CompositeDisposable disposable = new CompositeDisposable();

	private Database executedTransactionsDatabase;
	private Database tokenDefinitionDatabase;
	private Database balanceDatabase;

	@Inject
	public BerkeleyClientApiStore(
		DatabaseEnvironment dbEnv,
		BerkeleyLedgerEntryStore store,
		Serialization serialization,
		SystemCounters systemCounters,
		ScheduledEventDispatcher<ScheduledParticleFlush> scheduledFlushEventDispatcher,
		Observable<AtomsCommittedToLedger> ledgerCommitted
	) {
		this.dbEnv = dbEnv;
		this.store = store;
		this.serialization = serialization;
		this.systemCounters = systemCounters;
		this.scheduledFlushEventDispatcher = scheduledFlushEventDispatcher;
		this.ledgerCommitted = ledgerCommitted;

		open();
	}

	@Override
	public Result<List<TokenBalance>> getTokenBalances(RadixAddress address) {
		try (var cursor = balanceDatabase.openCursor(null, null)) {
			var key = asKey(address.toString());
			var data = entry();

			var status = readBalance(() -> cursor.getSearchKeyRange(key, data, null), data);

			if (status != OperationStatus.SUCCESS) {
				return Result.ok(List.of());
			}

			var list = new ArrayList<TokenBalance>();

			do {
				var entry = deserializeBalanceEntry(data.getData());

				if (!entry.isSuccess()) {
					log.error("Error deserializing existing balance while scanning DB for address {}", address);
				} else {
					entry.toOptional()
						.filter(Predicate.not(BalanceEntry::isSupply))
						.filter(Predicate.not(BalanceEntry::isStake))
						.map(TokenBalance::from)
						.ifPresent(list::add);
				}

				status = readBalance(() -> cursor.getNext(key, data, null), data);
			}
			while (status == OperationStatus.SUCCESS);

			return Result.ok(list);
		}
	}

	@Override
	public void storeCollectedParticles() {
		synchronized (particleCollector) {
			log.debug("Storing collected particles started");

			var count = withTime(
				() -> particleCollector.consumeCollected(this::storeSingleParticle),
				() -> systemCounters.increment(COUNT_APIDB_PARTICLE_FLUSH_COUNT),
				ELAPSED_APIDB_PARTICLE_FLUSH_TIME
			);

			inputCounter.addAndGet(-count);

			log.debug("Storing collected particles finished. {} particles processed", count);
		}
	}

	@Override
	public Result<UInt256> getTokenSupply(RRI rri) {
		try (var cursor = balanceDatabase.openCursor(null, null)) {
			var key = asKey(rri.toString());
			var data = entry();

			var status = readBalance(() -> cursor.getSearchKeyRange(key, data, null), data);

			if (status != OperationStatus.SUCCESS) {
				return Result.fail("Unknown RRI " + rri.toString());
			}

			return deserializeBalanceEntry(data.getData())
				.map(BalanceEntry::getAmount)
				.map(UInt256.MAX_VALUE::subtract);
		}
	}

	@Override
	public Result<TokenDefinitionRecord> getTokenDefinition(RRI rri) {
		try (var cursor = tokenDefinitionDatabase.openCursor(null, null)) {
			var key = asKey(rri.toString());
			var data = entry();

			var status = withTime(
				() -> cursor.getSearchKeyRange(key, data, null),
				() -> addTokenReadBytes(data),
				ELAPSED_APIDB_TOKEN_READ
			);

			if (status != OperationStatus.SUCCESS) {
				return Result.fail("Unknown RRI " + rri.toString());
			}

			return deserializeTokenDefinition(data.getData());
		}
	}

	@Override
	public EventProcessor<ScheduledParticleFlush> particleFlushProcessor() {
		return flush -> {
			storeCollectedParticles();
			scheduledFlushEventDispatcher.dispatch(ScheduledParticleFlush.create(), DEFAULT_FLUSH_INTERVAL);
		};
	}

	public void close() {
		disposable.dispose();
		storeCollectedParticles();

		safeClose(executedTransactionsDatabase);
		safeClose(balanceDatabase);
	}

	private <T> T readBalance(Supplier<T> supplier, DatabaseEntry data) {
		return withTime(supplier, () -> addBalanceReadBytes(data), ELAPSED_APIDB_BALANCE_READ);
	}

	private <T> T writeBalance(Supplier<T> supplier, DatabaseEntry data) {
		return withTime(supplier, () -> addBalanceWriteBytes(data), ELAPSED_APIDB_BALANCE_WRITE);
	}

	private void addBalanceReadBytes(DatabaseEntry data) {
		systemCounters.add(COUNT_APIDB_BALANCE_BYTES_READ, data.getSize());
		systemCounters.increment(COUNT_APIDB_BALANCE_READ);
		systemCounters.increment(COUNT_APIDB_BALANCE_TOTAL);
	}

	private void addBalanceWriteBytes(DatabaseEntry data) {
		systemCounters.add(COUNT_APIDB_BALANCE_BYTES_WRITE, data.getSize());
		systemCounters.increment(COUNT_APIDB_BALANCE_WRITE);
		systemCounters.increment(COUNT_APIDB_BALANCE_TOTAL);
	}

	private void addTokenReadBytes(DatabaseEntry data) {
		systemCounters.add(COUNT_APIDB_TOKEN_BYTES_READ, data.getSize());
		systemCounters.increment(COUNT_APIDB_TOKEN_READ);
		systemCounters.increment(COUNT_APIDB_TOKEN_TOTAL);
	}

	private void addTokenWriteBytes(DatabaseEntry data) {
		systemCounters.add(COUNT_APIDB_TOKEN_BYTES_WRITE, data.getSize());
		systemCounters.increment(COUNT_APIDB_TOKEN_WRITE);
		systemCounters.increment(COUNT_APIDB_TOKEN_TOTAL);
	}

	private <T> T withTime(Supplier<T> supplier, Runnable postAction, CounterType elapsedCounter) {
		final var start = System.nanoTime();
		try {
			return supplier.get();
		} finally {
			final var elapsed = (System.nanoTime() - start + 500L) / 1000L;
			this.systemCounters.add(elapsedCounter, elapsed);
			postAction.run();
		}
	}

	private Result<BalanceEntry> deserializeBalanceEntry(byte[] data) {
		try {
			return Result.ok(serialization.fromDson(data, BalanceEntry.class));
		} catch (DeserializeException e) {
			return Result.fail("Unable to deserialize balance entry from DB.");
		}
	}

	private Result<TokenDefinitionRecord> deserializeTokenDefinition(byte[] data) {
		try {
			return Result.ok(serialization.fromDson(data, TokenDefinitionRecord.class));
		} catch (DeserializeException e) {
			return Result.fail("Unable to deserialize token definition from DB.");
		}
	}

	private void open() {
		var config = new DatabaseConfig()
			.setAllowCreate(true)
			.setTransactional(true)
			.setKeyPrefixing(true)
			.setBtreeComparator(lexicographicalComparator());

		try {
			// This SuppressWarnings here is valid, as ownership of the underlying
			// resource is not changed here, the resource is just accessed.
			@SuppressWarnings("resource")
			var env = dbEnv.getEnvironment();
			balanceDatabase = env.openDatabase(null, BALANCE_DB, config);
			executedTransactionsDatabase = env.openDatabase(null, EXECUTED_TRANSACTIONS_DB, config);
			tokenDefinitionDatabase = env.openDatabase(null, TOKEN_DEFINITION_DB, config);

			if (System.getProperty("db.check_integrity", "1").equals("1")) {
				//TODO: Implement recovery, basically should be the same as fresh DB handling
			}

			if (executedTransactionsDatabase.count() == 0) {
				//Fresh DB, rebuild from log
				rebuildDatabase();
			}

			scheduledFlushEventDispatcher.dispatch(ScheduledParticleFlush.create(), DEFAULT_FLUSH_INTERVAL);

			disposable.add(ledgerCommitted
							   .observeOn(Schedulers.io())
							   .subscribe(this::processCommittedAtoms));

		} catch (Exception e) {
			throw new ClientApiStoreException("Error while opening databases", e);
		}
	}

	private void processCommittedAtoms(AtomsCommittedToLedger atomsCommittedToLedger) {
		atomsCommittedToLedger.getAtoms().forEach(cmd -> {
			try {
				serialization.fromDson(cmd.getPayload(), Atom.class)
					.uniqueInstructions()
					.map(instruction -> SpunParticle.of(instruction.getParticle(), instruction.getNextSpin()))
					.forEach(this::newParticle);
			} catch (DeserializeException e) {
				log.error("Error while deserializing atom committed to ledger. Skipping atom.", e);
			}
		});
	}

	private void safeClose(Database database) {
		if (database != null) {
			database.close();
		}
	}

	private void rebuildDatabase() {
		log.info("Database rebuilding is started");

		store.forEach(this::storeSingleParticle);

		log.info("Database rebuilding is finished successfully");
	}

	private void newParticle(SpunParticle particle) {
		particleCollector.push(particle);
		systemCounters.set(COUNT_APIDB_PARTICLE_QUEUE_SIZE, inputCounter.incrementAndGet());
	}

	private void storeSingleParticle(SpunParticle spunParticle) {
		if (spunParticle.getParticle() instanceof TokenDefinitionParticle) {
			storeTokenDefinition(spunParticle.getParticle());
		} else {
			//Store balance and supply
			if (spunParticle.getSpin() == Spin.DOWN) {
				storeSingleDownParticle(spunParticle.getParticle());
			} else {
				storeSingleUpParticle(spunParticle.getParticle());
			}
		}
	}

	private void storeTokenDefinition(Particle particle) {
		TokenDefinitionRecord.from(particle)
			.onSuccess(this::storeTokenDefinition)
			.onFailure(failure -> log.error("Unable to store token definition: {}", failure.message()));
	}

	private void storeTokenDefinition(TokenDefinitionRecord tokenDefinition) {
		var key = asKey(tokenDefinition.toKey());
		var value = serializeToEntry(entry(), tokenDefinition);
		var status = withTime(
			() -> tokenDefinitionDatabase.putNoOverwrite(null, key, value),
			() -> addTokenWriteBytes(value),
			ELAPSED_APIDB_TOKEN_WRITE
		);

		if (status != OperationStatus.SUCCESS) {
			log.error("Error while storing token definition {}", tokenDefinition.asJson());
		}
	}

	private void storeSingleUpParticle(Particle particle) {
		toBalanceEntry(particle).ifPresent(balanceEntry -> {
			var key = asKey(balanceEntry.toKey());
			var value = serializeToEntry(entry(), balanceEntry);

			mergeBalances(key, value, balanceEntry.negate());
		});
	}

	private void storeSingleDownParticle(Particle particle) {
		toBalanceEntry(particle).ifPresent(balanceEntry -> {
			var key = asKey(balanceEntry.toKey());

			mergeBalances(key, entry(), balanceEntry);
		});
	}

	private void mergeBalances(DatabaseEntry key, DatabaseEntry value, BalanceEntry balanceEntry) {
		var oldValue = entry();
		var status = readBalance(() -> balanceDatabase.get(null, key, oldValue, null), oldValue);

		if (status == OperationStatus.NOTFOUND) {
			// Negate the supply value
			serializeToEntry(value, balanceEntry.negate());
		} else if (status == OperationStatus.SUCCESS) {
			// Merge with existing balance
			var success = deserializeBalanceEntry(oldValue.getData())
				.map(existingBalance -> existingBalance.subtract(balanceEntry))
				.onSuccess(entry -> serializeToEntry(value, entry))
				.isSuccess();

			if (!success) {
				log.error("Error deserializing existing balance for {}", balanceEntry);
			}
		}

		status = writeBalance(() -> balanceDatabase.put(null, key, value), value);

		if (status != OperationStatus.SUCCESS) {
			log.error("Error while calculating merged balance {}", balanceEntry);
		}
	}

	private DatabaseEntry serializeToEntry(DatabaseEntry value, Object entry) {
		value.setData(serialization.toDson(entry, Output.ALL));
		return value;
	}

	private static DatabaseEntry asKey(String key) {
		return entry(key.getBytes(RadixConstants.STANDARD_CHARSET));
	}

	private Optional<BalanceEntry> toBalanceEntry(Particle p) {
		if (p instanceof StakedTokensParticle) {
			var a = (StakedTokensParticle) p;
			return Optional.of(BalanceEntry.create(
				a.getAddress(),
				a.getDelegateAddress(),
				a.getTokDefRef(),
				a.getGranularity(),
				a.getAmount()
			));
		} else if (p instanceof TransferrableTokensParticle) {
			var a = (TransferrableTokensParticle) p;
			return Optional.of(BalanceEntry.create(
				a.getAddress(),
				null,
				a.getTokDefRef(),
				a.getGranularity(),
				a.getAmount()
			));
		} else if (p instanceof UnallocatedTokensParticle) {
			var a = (UnallocatedTokensParticle) p;
			return Optional.of(BalanceEntry.create(
				a.getAddress(),
				null,
				a.getTokDefRef(),
				a.getGranularity(),
				a.getAmount()
			));
		}

		return Optional.empty();
	}

	static DatabaseEntry entry(byte[] data) {
		return new DatabaseEntry(data);
	}

	private static DatabaseEntry entry() {
		return new DatabaseEntry();
	}
}
