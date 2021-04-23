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
import com.google.inject.name.Named;
import com.radixdlt.api.construction.TxnParser;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.client.Rri;
import com.radixdlt.client.api.TxHistoryEntry;
import com.radixdlt.client.store.ClientApiStore;
import com.radixdlt.client.store.ClientApiStoreException;
import com.radixdlt.client.store.TokenDefinitionRecord;
import com.radixdlt.client.store.TransactionParser;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REParsedAction;
import com.radixdlt.constraintmachine.REParsedTxn;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.AtomsCommittedToLedger;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.OperationStatus;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_BALANCE_BYTES_READ;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_BALANCE_BYTES_WRITE;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_BALANCE_READ;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_BALANCE_TOTAL;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_BALANCE_WRITE;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_FLUSH_COUNT;
import static com.radixdlt.counters.SystemCounters.CounterType.COUNT_APIDB_QUEUE_SIZE;
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
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_APIDB_FLUSH_TIME;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_APIDB_TOKEN_READ;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_APIDB_TOKEN_WRITE;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_APIDB_TRANSACTION_READ;
import static com.radixdlt.counters.SystemCounters.CounterType.ELAPSED_APIDB_TRANSACTION_WRITE;
import static com.radixdlt.serialization.DsonOutput.Output;
import static com.radixdlt.serialization.SerializationUtils.restore;

public class BerkeleyClientApiStore implements ClientApiStore {
	private static final Logger log = LogManager.getLogger();

	private static final String EXECUTED_TRANSACTIONS_DB = "radix.executed_transactions_db";
	private static final String ADDRESS_BALANCE_DB = "radix.address.balance_db";
	private static final String SUPPLY_BALANCE_DB = "radix.supply.balance_db";
	private static final String TOKEN_DEFINITION_DB = "radix.token_definition_db";
	private static final String PENDING_UNSTAKES_DB = "radix.pending_unstakes_db";
	private static final long DEFAULT_FLUSH_INTERVAL = 100L;
	private static final int KEY_BUFFER_INITIAL_CAPACITY = 1024;
	private static final int TIMESTAMP_SIZE = Long.BYTES + Integer.BYTES;
	private static final Instant NOW = Instant.ofEpochMilli(Instant.now().toEpochMilli());

	private final DatabaseEnvironment dbEnv;
	private final BerkeleyLedgerEntryStore store;
	private final Serialization serialization;
	private final SystemCounters systemCounters;
	private final ScheduledEventDispatcher<ScheduledQueueFlush> scheduledFlushEventDispatcher;
	private final StackingCollector<AtomsCommittedToLedger> txCollector = StackingCollector.create();
	private final Observable<AtomsCommittedToLedger> ledgerCommitted;
	private final AtomicLong inputCounter = new AtomicLong();
	private final CompositeDisposable disposable = new CompositeDisposable();
	private final AtomicReference<Instant> currentTimestamp = new AtomicReference<>(NOW);
	private final byte universeMagic;
	private final TxnParser txnParser;
	private final TransactionParser transactionParser;
	private final ConstraintMachine constraintMachine;

	private Database transactionHistory;
	private Database tokenDefinitions;
	private Database addressBalances;
	private Database supplyBalances;
	private Database pendingUnstakes;

	@Inject
	public BerkeleyClientApiStore(
		DatabaseEnvironment dbEnv,
		ConstraintMachine constraintMachine,
		TxnParser txnParser,
		BerkeleyLedgerEntryStore store,
		Serialization serialization,
		SystemCounters systemCounters,
		ScheduledEventDispatcher<ScheduledQueueFlush> scheduledFlushEventDispatcher,
		Observable<AtomsCommittedToLedger> ledgerCommitted,
		TransactionParser transactionParser,
		@Named("magic") int universeMagic
	) {
		this.dbEnv = dbEnv;
		this.constraintMachine = constraintMachine;
		this.txnParser = txnParser;
		this.store = store;
		this.serialization = serialization;
		this.systemCounters = systemCounters;
		this.scheduledFlushEventDispatcher = scheduledFlushEventDispatcher;
		this.ledgerCommitted = ledgerCommitted;
		this.universeMagic = (byte) (universeMagic & 0xFF);
		this.transactionParser = transactionParser;

		open();
	}

	@Override
	public Result<REAddr> parseRri(String rri) {
		return Rri.parseFunctional(rri)
			.flatMap(p -> getTokenDefinition(p.getSecond())
				.flatMap(t -> Result.ok(p.getSecond()).filter(i -> t.getSymbol().equals(p.getFirst()), "symbol does not match"))
			);
	}

	@Override
	public Result<List<BalanceEntry>> getTokenBalances(RadixAddress address, boolean retrieveStakes) {
		try (var cursor = addressBalances.openCursor(null, null)) {
			var key = asKey(address);
			var data = entry();
			var status = readBalance(() -> cursor.getSearchKeyRange(key, data, null), data);

			if (status != OperationStatus.SUCCESS) {
				return Result.ok(List.of());
			}

			var list = new ArrayList<BalanceEntry>();

			do {
				restore(serialization, data.getData(), BalanceEntry.class)
					.onFailureDo(
						() -> log.error("Error deserializing existing balance while scanning DB for address {}", address)
					)
					.toOptional()
					.filter(entry -> entry.isStake() == retrieveStakes)
					.filter(entry -> entry.getOwner().equals(address))
					.ifPresent(list::add);

				status = readBalance(() -> cursor.getNext(key, data, null), data);
			}
			while (status == OperationStatus.SUCCESS);

			return Result.ok(list);
		}
	}

	@Override
	public Result<UInt384> getTokenSupply(String rri) {
		try (var cursor = supplyBalances.openCursor(null, null)) {
			var key = asKey(rri);
			var data = entry();

			var status = readBalance(() -> cursor.getSearchKeyRange(key, data, null), data);

			if (status == OperationStatus.NOTFOUND) {
				return Result.ok(UInt384.ZERO);
			}

			if (status != OperationStatus.SUCCESS) {
				return Result.fail("Unknown RRI " + rri.toString());
			}

			return restore(serialization, data.getData(), BalanceEntry.class)
				.onSuccess(entry -> log.trace("Stored token supply balance: {}", entry))
				.map(BalanceEntry::getAmount);
		}
	}

	@Override
	public Result<TokenDefinitionRecord> getTokenDefinition(REAddr addr) {
		try (var cursor = tokenDefinitions.openCursor(null, null)) {
			var key = asKey(addr);
			var data = entry();

			var status = withTime(
				() -> cursor.getSearchKeyRange(key, data, null),
				() -> addTokenReadBytes(data),
				ELAPSED_APIDB_TOKEN_READ
			);

			if (status != OperationStatus.SUCCESS) {
				return Result.fail("Unknown addr " + addr);
			}

			var definition = restore(serialization, data.getData(), TokenDefinitionRecord.class);
			if (definition.isSuccess()) {
				return definition;
			}

			return Result.fail("Unknown error getting addr " + addr);
		}
	}

	@Override
	public Result<TxHistoryEntry> getTransaction(AID txId) {
		return retrieveTx(txId)
			.flatMap(txn -> extractCreator(txn, universeMagic)
				.map(Result::ok)
				.orElseGet(() -> Result.fail("Unable to restore creator from transaction {0}", txn.getId()))
				.flatMap(creator -> lookupTransactionInHistory(creator, txn)));
	}

	@Override
	public Result<List<UnstakeEntry>> getUnstakePositions(RadixAddress radixAddress) {
		var key = asKey(radixAddress.getPublicKey(), Optional.empty(), Optional.empty());
		var data = entry();

		try (var cursor = pendingUnstakes.openCursor(null, null)) {
			var status = cursor.getSearchKeyRange(key, data, null);

			if (status != OperationStatus.SUCCESS) {
				return Result.ok(List.of());
			}

			var list = new ArrayList<UnstakeEntry>();

			do {
				restore(serialization, data.getData(), UnstakeEntry.class)
					.filter(entry -> pubKeyFromKeyEquals(key, radixAddress.getPublicKey()), "No match")
					.onSuccess(list::add);

				status = cursor.getNext(key, data, null);
			}
			while (status == OperationStatus.SUCCESS);

			return Result.ok(list);
		}
	}

	@Override
	public Result<List<TxHistoryEntry>> getTransactionHistory(RadixAddress address, int size, Optional<Instant> ptr) {
		if (size <= 0) {
			return Result.fail("Invalid size specified: {0}", size);
		}

		var instant = ptr.orElse(Instant.EPOCH);
		var key = asKey(address, instant);
		var data = entry();

		try (var cursor = transactionHistory.openCursor(null, null)) {
			var status = readTxHistory(() -> cursor.getSearchKeyRange(key, data, null), data);

			if (status != OperationStatus.SUCCESS) {
				return Result.ok(List.of());
			}

			// skip first entry if it's the same as the cursor
			if (instantFromKey(key).equals(instant)) {
				status = readTxHistory(() -> cursor.getNext(key, data, null), data);

				if (status != OperationStatus.SUCCESS) {
					return Result.ok(List.of());
				}
			}

			var list = new ArrayList<TxHistoryEntry>();

			do {
				AID.fromBytes(data.getData())
					.flatMap(this::retrieveTx)
					.onFailure(this::reportError)
					.flatMap(txnParser::parseTxn)
					.flatMap(txn -> transactionParser.parse(txn, instantFromKey(key)))
					.onSuccess(list::add);

				status = readTxHistory(() -> cursor.getNext(key, data, null), data);
			}
			while (status == OperationStatus.SUCCESS && list.size() < size);

			return Result.ok(list);
		}
	}

	@Override
	public EventProcessor<ScheduledQueueFlush> queueFlushProcessor() {
		return flush -> {
			storeCollected();
			scheduledFlushEventDispatcher.dispatch(ScheduledQueueFlush.create(), DEFAULT_FLUSH_INTERVAL);
		};
	}

	public void close() {
		disposable.dispose();
		storeCollected();

		safeClose(transactionHistory);
		safeClose(tokenDefinitions);
		safeClose(addressBalances);
		safeClose(supplyBalances);
	}

	private void storeCollected() {
		synchronized (txCollector) {
			log.trace("Storing collected transactions started");

			var count = withTime(
				() -> txCollector.consumeCollected(this::storeTransactionBatch),
				() -> systemCounters.increment(COUNT_APIDB_FLUSH_COUNT),
				ELAPSED_APIDB_FLUSH_TIME
			);

			inputCounter.addAndGet(-count);

			log.trace("Storing collected transactions finished. {} transactions processed", count);
		}
	}

	private Result<TxHistoryEntry> lookupTransactionInHistory(RadixAddress creator, Txn txn) {
		var key = asKey(creator, Instant.EPOCH);
		var data = entry();

		try (var cursor = transactionHistory.openCursor(null, null)) {
			var status = readTxHistory(() -> cursor.getSearchKeyRange(key, data, null), data);

			if (status != OperationStatus.SUCCESS) {
				return errorTxNotFound(txn);
			}

			do {
				if (AID.fromBytes(data.getData()).fold(__ -> false, aid -> aid.equals(txn.getId()))) {
					return Result.ok(txn)
						.flatMap(txnParser::parseTxn)
						.flatMap(t -> transactionParser.parse(t, instantFromKey(key)));
				}

				status = readTxHistory(() -> cursor.getNext(key, data, null), data);
			}
			while (status == OperationStatus.SUCCESS);
		}
		return errorTxNotFound(txn);
	}

	private Result<TxHistoryEntry> errorTxNotFound(Txn txn) {
		return Result.fail("Transaction with id {0} not found", txn.getId());
	}

	private Instant instantFromKey(DatabaseEntry key) {
		var buf = Unpooled.wrappedBuffer(key.getData(), key.getSize() - TIMESTAMP_SIZE, TIMESTAMP_SIZE);
		return Instant.ofEpochSecond(buf.readLong(), buf.readInt());
	}

	private Result<Txn> retrieveTx(AID id) {
		return store.get(id)
			.map(Result::ok)
			.orElseGet(() -> Result.fail("Unable to retrieve transaction by ID {0} ", id));
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

	private void open() {
		try {
			// This SuppressWarnings here is valid, as ownership of the underlying
			// resource is not changed here, the resource is just accessed.
			@SuppressWarnings("resource")
			var env = dbEnv.getEnvironment();
			var uniqueConfig = createUniqueConfig();

			addressBalances = env.openDatabase(null, ADDRESS_BALANCE_DB, uniqueConfig);
			supplyBalances = env.openDatabase(null, SUPPLY_BALANCE_DB, uniqueConfig);
			tokenDefinitions = env.openDatabase(null, TOKEN_DEFINITION_DB, uniqueConfig);
			transactionHistory = env.openDatabase(null, EXECUTED_TRANSACTIONS_DB, uniqueConfig);
			pendingUnstakes = env.openDatabase(null, PENDING_UNSTAKES_DB, createDuplicateConfig());

			if (System.getProperty("db.check_integrity", "1").equals("1")) {
				//TODO: Implement recovery, basically should be the same as fresh DB handling
			}

			if (addressBalances.count() == 0) {
				//Fresh DB, rebuild from log
				rebuildDatabase();
			}

			scheduledFlushEventDispatcher.dispatch(ScheduledQueueFlush.create(), DEFAULT_FLUSH_INTERVAL);

			disposable.add(ledgerCommitted.subscribe(this::newBatch));

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

	private DatabaseConfig createDuplicateConfig() {
		return new DatabaseConfig()
			.setAllowCreate(true)
			.setTransactional(true)
			.setSortedDuplicates(true)
			.setBtreeComparator(lexicographicalComparator());
	}

	private void reportError(Failure failure) {
		log.error(failure.message());
	}

	private void safeClose(Database database) {
		if (database != null) {
			database.close();
		}
	}

	private void rebuildDatabase() {
		log.info("Database rebuilding is started");
		store.forEach(txn -> txnParser.parseTxn(txn).onSuccess(this::processRETransaction));
		log.info("Database rebuilding is finished successfully");
	}

	private void newBatch(AtomsCommittedToLedger transactions) {
		txCollector.push(transactions);
		systemCounters.set(COUNT_APIDB_QUEUE_SIZE, inputCounter.addAndGet(transactions.getTxns().size()));
	}

	private void storeTransactionBatch(AtomsCommittedToLedger act) {
		act.getParsedTxs().forEach(this::processRETransaction);
	}

	private void processRETransaction(REParsedTxn reTxn) {
		extractTimestamp(reTxn.upSubstates());

		var id = reTxn.getTxn().getId();

		reTxn.getUser().ifPresentOrElse(
			p -> {
				var addr = new RadixAddress(universeMagic, p);
				storeSingleTransaction(id, addr);
				reTxn.getActions().forEach(a -> storeAction(addr, a, id));
			},
			() -> reTxn.getActions().forEach(a -> storeAction(null, a, id))
		);
	}

	private void storeAction(RadixAddress user, REParsedAction action, AID id) {
		var txAction = action.getTxAction();

		if (txAction instanceof TransferToken) {
			var transferToken = (TransferToken) txAction;

			var rri = getTokenDefinition(transferToken.addr()).toOptional().orElseThrow().rri();
			var entry0 = BalanceEntry.create(
				user,
				null,
				rri,
				UInt384.from(transferToken.amount()),
				true
			);

			var entry1 = BalanceEntry.create(
				transferToken.to(),
				null,
				rri,
				UInt384.from(transferToken.amount()),
				false
			);

			storeBalanceEntry(entry0);
			storeBalanceEntry(entry1);

			checkPendingUnstake(user, transferToken);
		} else if (txAction instanceof BurnToken) {
			var burnToken = (BurnToken) txAction;

			var rri = getTokenDefinition(burnToken.addr()).toOptional().orElseThrow().rri();
			var entry0 = BalanceEntry.create(
				user,
				null,
				rri,
				UInt384.from(burnToken.amount()),
				true
			);

			var entry1 = BalanceEntry.create(
				null,
				null,
				rri,
				UInt384.from(burnToken.amount()),
				true
			);

			storeBalanceEntry(entry0);
			storeBalanceEntry(entry1);
		} else if (txAction instanceof MintToken) {
			var mintToken = (MintToken) txAction;

			var rri = getTokenDefinition(mintToken.addr()).toOptional().orElseThrow().rri();
			var entry0 = BalanceEntry.create(
				mintToken.to(),
				null,
				rri,
				UInt384.from(mintToken.amount()),
				false
			);

			var entry1 = BalanceEntry.create(
				null,
				null,
				rri,
				UInt384.from(mintToken.amount()),
				false
			);

			storeBalanceEntry(entry0);
			storeBalanceEntry(entry1);
		} else if (txAction instanceof CreateMutableToken) {
			var createMutableToken = (CreateMutableToken) txAction;
			var record = TokenDefinitionRecord.from(user, createMutableToken);

			storeTokenDefinition(record);
		} else if (txAction instanceof CreateFixedToken) {
			var createFixedToken = (CreateFixedToken) txAction;
			var record = TokenDefinitionRecord.from(user, createFixedToken);

			storeTokenDefinition(record);
		} else if (txAction instanceof UnstakeTokens) {
			createPendingUnstake(user, (UnstakeTokens) txAction, id);
		}
	}

	private void checkPendingUnstake(RadixAddress user, TransferToken transferToken) {
		if (user != null) {
			var key = asKey(
				transferToken.to().getPublicKey(),
				Optional.of(user.getPublicKey()),
				Optional.of(transferToken.amount())
			);
			pendingUnstakes.delete(null, key);
		}
	}

	private void createPendingUnstake(RadixAddress user, UnstakeTokens unstake, AID id) {
		var entry = UnstakeEntry.create(
			unstake.amount(),
			new RadixAddress(universeMagic, unstake.from()),
			0, //TODO: fix 'epochsUntil' when it will be implemented
			id
		);
		var key = asKey(user.getPublicKey(), Optional.of(unstake.from()), Optional.of(unstake.amount()));
		var data = serializeTo(entry(), entry);
		var status = pendingUnstakes.putNoOverwrite(null, key, data);

		if (status != OperationStatus.SUCCESS) {
			log.warn("Pending unstake exists {} for {}", entry, user);
		}
	}

	private void extractTimestamp(Stream<Particle> upParticles) {
		upParticles.filter(SystemParticle.class::isInstance)
			.map(SystemParticle.class::cast)
			.findFirst()
			.map(SystemParticle::asInstant)
			.ifPresent(currentTimestamp::set);
	}

	private Optional<RadixAddress> extractCreator(Txn tx, byte universeMagic) {
		try {
			var result = constraintMachine.statelessVerify(tx);
			return result.getRecovered().map(pk -> new RadixAddress(universeMagic, pk));
		} catch (RadixEngineException e) {
			throw new IllegalStateException();
		}
	}

	private void storeSingleTransaction(AID id, RadixAddress creator) {
		//Note: since Java 9 the Clock.systemUTC() produces values with real nanosecond resolution.
		var key = asKey(creator, currentTimestamp.get());
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

	private void storeTokenDefinition(TokenDefinitionRecord tokenDefinition) {
		var key = asKey(tokenDefinition.addr());
		var value = serializeTo(entry(), tokenDefinition);
		var status = withTime(
			() -> tokenDefinitions.putNoOverwrite(null, key, value),
			() -> addTokenWriteBytes(value),
			ELAPSED_APIDB_TOKEN_WRITE
		);

		if (status != OperationStatus.SUCCESS) {
			log.error("Error while storing token definition {}", tokenDefinition.asJson());
		}
	}

	private void storeBalanceEntry(BalanceEntry entry) {
		var key = entry.isSupply() ? asKey(entry.rri()) : asKey(entry);
		mergeBalances(key, entry(), entry);
	}

	private void mergeBalances(DatabaseEntry key, DatabaseEntry value, BalanceEntry balanceEntry) {
		var database = balanceEntry.isSupply() ? supplyBalances : addressBalances;
		var oldValue = entry();
		var status = readBalance(() -> database.get(null, key, oldValue, null), oldValue);

		if (status == OperationStatus.NOTFOUND) {
			serializeTo(value, balanceEntry);
		} else if (status == OperationStatus.SUCCESS) {
			// Merge with existing balance
			restore(serialization, oldValue.getData(), BalanceEntry.class)
				.map(existingBalance -> existingBalance.add(balanceEntry))
				.onSuccess(entry -> serializeTo(value, entry))
				.onFailure(this::reportError);
		}

		status = writeBalance(() -> database.put(null, key, value), value);

		if (status != OperationStatus.SUCCESS) {
			log.error("Error while calculating merged balance {}", balanceEntry);
		}
	}

	private DatabaseEntry serializeTo(DatabaseEntry value, Object entry) {
		value.setData(serialization.toDson(entry, Output.ALL));
		return value;
	}

	private static DatabaseEntry asKey(String rri) {
		return entry(rri.getBytes(StandardCharsets.UTF_8));
	}

	private static DatabaseEntry asKey(BalanceEntry balanceEntry) {
		var address = buffer().writeBytes(balanceEntry.getOwner().getPublicKey().getCompressedBytes());
		var buf = address.writeBytes(balanceEntry.rri().getBytes(StandardCharsets.UTF_8));

		if (balanceEntry.isStake()) {
			buf.writeBytes(balanceEntry.getDelegate().getPublicKey().getCompressedBytes());
		} else {
			buf.writeZero(ECPublicKey.COMPRESSED_BYTES);
		}

		return entry(buf);
	}

	private static DatabaseEntry asKey(RadixAddress radixAddress) {
		return entry(buffer()
						 .writeBytes(radixAddress.getPublicKey().getCompressedBytes())
						 .writeZero(ECPublicKey.COMPRESSED_BYTES));
	}

	private static DatabaseEntry asKey(REAddr addr) {
		return entry(addr.getBytes());
	}

	private static DatabaseEntry asKey(ECPublicKey user, Optional<ECPublicKey> validator, Optional<UInt256> amount) {
		var buffer = buffer().writeBytes(user.getCompressedBytes());

		validator.ifPresentOrElse(
			v -> buffer.writeBytes(v.getCompressedBytes()),
			() -> buffer.writeZero(ECPublicKey.COMPRESSED_BYTES)
		);

		amount.ifPresentOrElse(
			a -> buffer.writeBytes(a.toByteArray()),
			() -> buffer.writeZero(UInt256.BYTES)
		);

		return entry(buffer);
	}

	//Key format must match one produced by method above
	private boolean pubKeyFromKeyEquals(DatabaseEntry key, ECPublicKey publicKey) {
		var bytes = Arrays.copyOf(key.getData(), ECPublicKey.COMPRESSED_BYTES);

		return Arrays.equals(bytes, publicKey.getCompressedBytes());
	}

	private static DatabaseEntry asKey(RadixAddress radixAddress, Instant timestamp) {
		return entry(buffer()
						 .writeBytes(radixAddress.toByteArray())
						 .writeLong(timestamp.getEpochSecond())
						 .writeInt(timestamp.getNano()));
	}

	private static ByteBuf buffer() {
		return Unpooled.buffer(KEY_BUFFER_INITIAL_CAPACITY);
	}

	private static DatabaseEntry entry(byte[] data) {
		return new DatabaseEntry(data);
	}

	private static DatabaseEntry entry(ByteBuf buf) {
		return new DatabaseEntry(buf.array(), 0, buf.readableBytes());
	}

	private static DatabaseEntry entry() {
		return new DatabaseEntry();
	}
}
