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
import com.radixdlt.constraintmachine.ParsedInstruction;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.store.ActionEntry;
import com.radixdlt.client.store.ClientApiStore;
import com.radixdlt.client.store.ClientApiStoreException;
import com.radixdlt.client.store.MessageEntry;
import com.radixdlt.client.store.TokenBalance;
import com.radixdlt.client.store.TokenDefinitionRecord;
import com.radixdlt.client.store.TxHistoryEntry;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.AtomsCommittedToLedger;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;
import com.radixdlt.utils.functional.Tuple;
import com.radixdlt.utils.functional.Tuple.Tuple2;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.OperationStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;
import static com.radixdlt.client.store.MessageEntry.fromPlainString;
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
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_TRANSACTION_BYTES_READ;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_TRANSACTION_BYTES_WRITE;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_TRANSACTION_READ;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_TRANSACTION_TOTAL;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_TRANSACTION_WRITE;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_APIDB_BALANCE_READ;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_APIDB_BALANCE_WRITE;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_APIDB_PARTICLE_FLUSH_TIME;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_APIDB_TOKEN_READ;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_APIDB_TOKEN_WRITE;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_APIDB_TRANSACTION_READ;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_APIDB_TRANSACTION_WRITE;
import static com.radixdlt.serialization.DsonOutput.Output;
import static com.radixdlt.utils.functional.Tuple.tuple;

public class BerkeleyClientApiStore implements ClientApiStore {
	private static final Logger log = LogManager.getLogger();

	private static final String EXECUTED_TRANSACTIONS_DB = "radix.executed_transactions_db";
	private static final String BALANCE_DB = "radix.balance_db";
	private static final String TOKEN_DEFINITION_DB = "radix.token_definition_db";
	private static final long DEFAULT_FLUSH_INTERVAL = 100L;
	private static final int KEY_BUFFER_INITIAL_CAPACITY = 1024;
	private static final int TIMESTAMP_SIZE = Long.BYTES + Integer.BYTES;

	private final DatabaseEnvironment dbEnv;
	private final BerkeleyLedgerEntryStore store;
	private final Serialization serialization;
	private final SystemCounters systemCounters;
	private final ScheduledEventDispatcher<ScheduledParticleFlush> scheduledFlushEventDispatcher;
	private final StackingCollector<ParsedInstruction> particleCollector = StackingCollector.create();
	private final Observable<AtomsCommittedToLedger> ledgerCommitted;
	private final AtomicLong inputCounter = new AtomicLong();
	private final CompositeDisposable disposable = new CompositeDisposable();

	private Database transactionHistory;
	private Database tokenDefinitions;
	private Database balances;

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
		try (var cursor = balances.openCursor(null, null)) {
			var key = asKey(address);
			var data = entry();
			var status = readBalance(() -> cursor.getSearchKeyRange(key, data, null), data);

			if (status != OperationStatus.SUCCESS) {
				return Result.ok(List.of());
			}

			var list = new ArrayList<TokenBalance>();

			do {
				deserializeBalanceEntry(data.getData())
					.onFailureDo(
						() -> log.error("Error deserializing existing balance while scanning DB for address {}", address)
					)
					.toOptional()
					.filter(Predicate.not(BalanceEntry::isSupply))
					.filter(Predicate.not(BalanceEntry::isStake))
					.map(TokenBalance::from)
					.ifPresent(list::add);

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
		try (var cursor = balances.openCursor(null, null)) {
			var key = asKey(rri);
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
		try (var cursor = tokenDefinitions.openCursor(null, null)) {
			var key = asKey(rri);
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
	public Result<List<TxHistoryEntry>> getTransactionHistory(RadixAddress address, int size, Optional<Instant> ptr) {
		var key = asKey(address, ptr.orElse(Instant.EPOCH));
		var data = entry();

		try (var cursor = transactionHistory.openCursor(null, null)) {
			var status = readTxHistory(() -> cursor.getSearchKeyRange(key, data, null), data);

			if (status != OperationStatus.SUCCESS) {
				return Result.ok(List.of());
			}

			var list = new ArrayList<TxHistoryEntry>();

			do {
				deserializeTxId(data.getData())
					.flatMap(this::retrieveTx)
					.onFailureDo(
						() -> log.error("Error deserializing TxID while scanning DB for address {}", address)
					)
					.map(txWithId -> txWithId.map((id, tx) -> parseTransaction(id, tx, instantFromKey(key))))
					.onSuccess(list::add);

				status = readTxHistory(() -> cursor.getNext(key, data, null), data);
			}
			while (status == OperationStatus.SUCCESS);

			return Result.ok(list);
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

		safeClose(transactionHistory);
		safeClose(balances);
	}

	private Instant instantFromKey(DatabaseEntry key) {
		var buf = Unpooled.wrappedBuffer(key.getData(), key.getSize() - TIMESTAMP_SIZE, key.getSize());
		return Instant.ofEpochSecond(buf.readLong(), buf.readInt());
	}

	private TxHistoryEntry parseTransaction(AID id, Atom tx, Instant instant) {
		//TODO: finish
//		this.txId = txId;
//		this.date = date;
//		this.fee = fee;
//		this.message = message;
//		this.actions = actions;
		var fee = UInt256.ZERO;
		var actions = new ArrayList<ActionEntry>();



		return TxHistoryEntry.create(id, instant, fee, fromPlainString(tx.getMessage()).orElse(null), actions);
	}

	private Result<Tuple2<AID, Atom>> retrieveTx(AID id) {
		return store.get(id)
			.map(tx -> Result.ok(tuple(id, tx)))
			.orElseGet(() -> Result.fail("Unable to retrieve transaction by ID {} ", id));
	}

	private <T> T readBalance(Supplier<T> supplier, DatabaseEntry data) {
		return withTime(supplier, () -> addBalanceReadBytes(data), ELAPSED_APIDB_BALANCE_READ);
	}

	private <T> T writeBalance(Supplier<T> supplier, DatabaseEntry data) {
		return withTime(supplier, () -> addBalanceWriteBytes(data), ELAPSED_APIDB_BALANCE_WRITE);
	}

	private <T> T readTxHistory(Supplier<T> supplier, DatabaseEntry data) {
		return withTime(supplier, () -> addTxHistoryReadBytes(data), ELAPSED_APIDB_TRANSACTION_READ);
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

	private void addTxHistoryReadBytes(DatabaseEntry data) {
		systemCounters.add(COUNT_APIDB_TRANSACTION_BYTES_READ, data.getSize());
		systemCounters.increment(COUNT_APIDB_TRANSACTION_READ);
		systemCounters.increment(COUNT_APIDB_TRANSACTION_TOTAL);
	}

	private void addTxHistoryWriteBytes(DatabaseEntry data) {
		systemCounters.add(COUNT_APIDB_TRANSACTION_BYTES_WRITE, data.getSize());
		systemCounters.increment(COUNT_APIDB_TRANSACTION_WRITE);
		systemCounters.increment(COUNT_APIDB_TRANSACTION_TOTAL);
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
			return Result.fail("Unable to deserialize balance entry.");
		}
	}

	private Result<TokenDefinitionRecord> deserializeTokenDefinition(byte[] data) {
		try {
			return Result.ok(serialization.fromDson(data, TokenDefinitionRecord.class));
		} catch (DeserializeException e) {
			return Result.fail("Unable to deserialize token definition.");
		}
	}

	private Result<AID> deserializeTxId(byte[] data) {
		try {
			return Result.ok(serialization.fromDson(data, AID.class));
		} catch (DeserializeException e) {
			return Result.fail("Unable to deserialize transaction ID.");
		}
	}

	private void open() {
		try {
			// This SuppressWarnings here is valid, as ownership of the underlying
			// resource is not changed here, the resource is just accessed.
			@SuppressWarnings("resource")
			var env = dbEnv.getEnvironment();
			var uniqueConfig = createUniqueConfig();

			balances = env.openDatabase(null, BALANCE_DB, uniqueConfig);
			tokenDefinitions = env.openDatabase(null, TOKEN_DEFINITION_DB, uniqueConfig);
			transactionHistory = env.openDatabase(null, EXECUTED_TRANSACTIONS_DB, uniqueConfig);

			if (System.getProperty("db.check_integrity", "1").equals("1")) {
				//TODO: Implement recovery, basically should be the same as fresh DB handling
			}

			if (balances.count() == 0) {
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

	private DatabaseConfig createUniqueConfig() {
		return new DatabaseConfig()
			.setAllowCreate(true)
			.setTransactional(true)
			.setKeyPrefixing(true)
			.setBtreeComparator(lexicographicalComparator());
	}

	private void processCommittedAtoms(AtomsCommittedToLedger atomsCommittedToLedger) {
		atomsCommittedToLedger.getParsedTxs().stream()
			.flatMap(tx -> tx.instructions().stream())
			.forEach(this::newParticle);
	}

	private Optional<RadixAddress> extractCreator(Atom tx) {
		var address = new AtomicReference<RadixAddress>();

		//TODO: finish it
//		tx.getMicroInstructions().forEach(mi -> {
//			if (mi.getNextSpin()
//			mi.getParticle()
//		});

		return Optional.ofNullable(address.get());
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

	private void newParticle(ParsedInstruction instruction) {
		particleCollector.push(instruction);
		systemCounters.set(COUNT_APIDB_PARTICLE_QUEUE_SIZE, inputCounter.incrementAndGet());
	}

	private void storeSingleParticle(ParsedInstruction parsedInstruction) {
		if (parsedInstruction.getParticle() instanceof TokenDefinitionParticle) {
			storeTokenDefinition(parsedInstruction.getParticle());
		} else {
			//Store balance and supply
			if (parsedInstruction.getSpin() == Spin.DOWN) {
				storeSingleDownParticle(parsedInstruction.getParticle());
			} else {
				storeSingleUpParticle(parsedInstruction.getParticle());
			}
		}
	}

	private void storeTokenDefinition(Particle substate) {
		TokenDefinitionRecord.from(substate)
			.onSuccess(this::storeTokenDefinition)
			.onFailure(failure -> log.error("Unable to store token definition: {}", failure.message()));
	}

	private void storeTokenDefinition(TokenDefinitionRecord tokenDefinition) {
		var key = asKey(tokenDefinition.rri());
		var value = serializeToEntry(entry(), tokenDefinition);
		var status = withTime(
			() -> tokenDefinitions.putNoOverwrite(null, key, value),
			() -> addTokenWriteBytes(value),
			ELAPSED_APIDB_TOKEN_WRITE
		);

		if (status != OperationStatus.SUCCESS) {
			log.error("Error while storing token definition {}", tokenDefinition.asJson());
		}
	}

	private void storeSingleTransaction(RadixAddress creator, AID id) {
		//Note: since Java 9 the Clock.systemUTC() produces values with real nanosecond resolution.
		var key = asKey(creator, Instant.now());
		var data = entry(id.getBytes());

		var status = withTime(
			() -> transactionHistory.put(null, key, data),
			() -> addTxHistoryWriteBytes(data),
			ELAPSED_APIDB_TRANSACTION_WRITE
		);

		if (status != OperationStatus.SUCCESS) {
			log.error("Error while storing transaction {} for {}", id, creator);
		}
	}

	private void storeSingleUpParticle(Particle substate) {
		toBalanceEntry(substate).ifPresent(balanceEntry -> {
			var key = asKey(balanceEntry);
			var value = serializeToEntry(entry(), balanceEntry);

			mergeBalances(key, value, balanceEntry.negate());
		});
	}

	private void storeSingleDownParticle(Particle substate) {
		toBalanceEntry(substate).ifPresent(balanceEntry -> {
			var key = asKey(balanceEntry);

			mergeBalances(key, entry(), balanceEntry);
		});
	}

	private void mergeBalances(DatabaseEntry key, DatabaseEntry value, BalanceEntry balanceEntry) {
		var oldValue = entry();
		var status = readBalance(() -> balances.get(null, key, oldValue, null), oldValue);

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

		status = writeBalance(() -> balances.put(null, key, value), value);

		if (status != OperationStatus.SUCCESS) {
			log.error("Error while calculating merged balance {}", balanceEntry);
		}
	}

	private DatabaseEntry serializeToEntry(DatabaseEntry value, Object entry) {
		value.setData(serialization.toDson(entry, Output.ALL));
		return value;
	}

	private static DatabaseEntry asKey(BalanceEntry balanceEntry) {
		var buf = writeRRI(buffer().writeBytes(balanceEntry.getOwner().toByteArray()), balanceEntry.getRri());

		if (balanceEntry.isStake()) {
			buf.writeBytes(balanceEntry.getDelegate().toByteArray());
		}

		return entry(buf);
	}

	private static DatabaseEntry asKey(RRI rri) {
		return entry(writeRRI(buffer(), rri));
	}

	private static DatabaseEntry asKey(RadixAddress radixAddress) {
		return entry(buffer().writeBytes(radixAddress.toByteArray()));
	}

	private static DatabaseEntry asKey(RadixAddress radixAddress, Instant timestamp) {
		return entry(buffer()
						 .writeBytes(radixAddress.toByteArray())
						 .writeLong(timestamp.getEpochSecond())
						 .writeInt(timestamp.getNano()));
	}

	private static ByteBuf writeRRI(ByteBuf buf, RRI rri) {
		return buf.writeBytes(rri.getAddress().toByteArray()).writeBytes(rri.getName().getBytes());
	}

	private static ByteBuf buffer() {
		return Unpooled.buffer(KEY_BUFFER_INITIAL_CAPACITY);
	}

	private static DatabaseEntry entry(byte[] data) {
		return new DatabaseEntry(data);
	}

	private static DatabaseEntry entry(ByteBuf buf) {
		return new DatabaseEntry(buf.array());
	}

	private static DatabaseEntry entry() {
		return new DatabaseEntry();
	}

	private Optional<BalanceEntry> toBalanceEntry(Particle substate) {
		if (substate instanceof StakedTokensParticle) {
			var a = (StakedTokensParticle) substate;
			return Optional.of(BalanceEntry.create(
				a.getAddress(),
				a.getDelegateAddress(),
				a.getTokDefRef(),
				a.getGranularity(),
				a.getAmount()
			));
		} else if (substate instanceof TransferrableTokensParticle) {
			var a = (TransferrableTokensParticle) substate;
			return Optional.of(BalanceEntry.create(
				a.getAddress(),
				null,
				a.getTokDefRef(),
				a.getGranularity(),
				a.getAmount()
			));
		} else if (substate instanceof UnallocatedTokensParticle) {
			var a = (UnallocatedTokensParticle) substate;
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
}
