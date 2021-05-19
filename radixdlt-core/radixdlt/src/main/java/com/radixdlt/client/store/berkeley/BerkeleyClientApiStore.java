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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.radixdlt.api.construction.TxnParser;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.StakeTokens;
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
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.AtomsCommittedToLedger;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;
import static com.radixdlt.client.api.ApiErrors.INVALID_PAGE_SIZE;
import static com.radixdlt.client.api.ApiErrors.SYMBOL_DOES_NOT_MATCH;
import static com.radixdlt.client.api.ApiErrors.UNABLE_TO_RESTORE_CREATOR;
import static com.radixdlt.client.api.ApiErrors.UNKNOWN_ACCOUNT_ADDRESS;
import static com.radixdlt.client.api.ApiErrors.UNKNOWN_RRI;
import static com.radixdlt.client.api.ApiErrors.UNKNOWN_TX_ID;
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
import static com.radixdlt.identifiers.CommonErrors.INVALID_ACCOUNT_ADDRESS;
import static com.radixdlt.serialization.DsonOutput.Output;
import static com.radixdlt.serialization.SerializationUtils.restore;

public class BerkeleyClientApiStore implements ClientApiStore {
	private static final Logger log = LogManager.getLogger();

	private static final String EXECUTED_TRANSACTIONS_DB = "radix.executed_transactions_db";
	private static final String ADDRESS_BALANCE_DB = "radix.address.balance_db";
	private static final String SUPPLY_BALANCE_DB = "radix.supply.balance_db";
	private static final String TOKEN_DEFINITION_DB = "radix.token_definition_db";
	private static final long DEFAULT_FLUSH_INTERVAL = 100L;
	private static final int KEY_BUFFER_INITIAL_CAPACITY = 1024;
	private static final int TIMESTAMP_SIZE = Long.BYTES + Integer.BYTES;
	private static final Instant NOW = Instant.ofEpochMilli(Instant.now().toEpochMilli());
	private static final Failure IGNORED = Failure.failure(0, "Ignored");

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
	private final TxnParser txnParser;
	private final TransactionParser transactionParser;
	private final ConstraintMachine constraintMachine;

	private Database transactionHistory;
	private Database tokenDefinitions;
	private Database addressBalances;
	private Database supplyBalances;

	private final Cache<REAddr, String> rriCache = CacheBuilder.newBuilder()
		.maximumSize(1024)
		.build();

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
		boolean isTest
	) {
		this.dbEnv = dbEnv;
		this.constraintMachine = constraintMachine;
		this.txnParser = txnParser;
		this.store = store;
		this.serialization = serialization;
		this.systemCounters = systemCounters;
		this.scheduledFlushEventDispatcher = scheduledFlushEventDispatcher;
		this.ledgerCommitted = ledgerCommitted;
		this.transactionParser = transactionParser;

		open(isTest);
	}

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
		TransactionParser transactionParser
	) {
		this.dbEnv = dbEnv;
		this.constraintMachine = constraintMachine;
		this.txnParser = txnParser;
		this.store = store;
		this.serialization = serialization;
		this.systemCounters = systemCounters;
		this.scheduledFlushEventDispatcher = scheduledFlushEventDispatcher;
		this.ledgerCommitted = ledgerCommitted;
		this.transactionParser = transactionParser;

		open(false);
	}

	@Override
	public Result<REAddr> parseRri(String rri) {
		return Rri.parseFunctional(rri)
			.flatMap(
				p -> getTokenDefinition(p.getSecond())
					.flatMap(t -> Result.ok(p.getSecond())
						.filter(i -> t.getSymbol().equals(p.getFirst()), SYMBOL_DOES_NOT_MATCH.with(t.getSymbol())))
			);
	}

	@Override
	public Result<List<BalanceEntry>> getTokenBalances(REAddr addr, boolean retrieveStakes) {
		try (var cursor = addressBalances.openCursor(null, null)) {
			var key = asKey(addr);
			var data = entry();
			var status = readBalance(() -> cursor.getSearchKeyRange(key, data, null), data);

			if (status != OperationStatus.SUCCESS) {
				return Result.ok(List.of());
			}

			var list = new ArrayList<BalanceEntry>();

			do {
				restore(serialization, data.getData(), BalanceEntry.class)
					.onFailureDo(
						() -> log.error("Error deserializing existing balance while scanning DB for address {}", addr)
					)
					.toOptional()
					.filter(entry -> entry.isStake() == retrieveStakes)
					.filter(entry -> entry.getOwner().equals(addr))
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
				return UNKNOWN_RRI.with(rri).result();
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
				return UNKNOWN_ACCOUNT_ADDRESS.with(addr).result();
			}

			return restore(serialization, data.getData(), TokenDefinitionRecord.class)
				.onFailure(log::error);
		}
	}

	private String getRriOrFail(REAddr addr) {
		try {
			return rriCache.get(addr, () -> getTokenDefinition(addr).toOptional().orElseThrow().rri());
		} catch (ExecutionException e) {
			log.error("Unable to find rri of token at address {}", addr);
			throw new IllegalStateException(e);
		}
	}

	@Override
	public Result<TxHistoryEntry> getTransaction(AID txId) {
		return retrieveTx(txId)
			.flatMap(txn -> extractCreator(txn)
				.map(REAddr::ofPubKeyAccount)
				.map(Result::ok)
				.orElseGet(() -> UNABLE_TO_RESTORE_CREATOR.with(txn.getId()).result())
				.flatMap(creator -> lookupTransactionInHistory(creator, txn)));
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

	private Result<TxHistoryEntry> lookupTransactionInHistory(REAddr addr, Txn txn) {
		var key = asKey(addr, Instant.EPOCH);
		var data = entry();

		try (var cursor = transactionHistory.openCursor(null, null)) {
			var status = readTxHistory(() -> cursor.getSearchKeyRange(key, data, null), data);

			if (status != OperationStatus.SUCCESS) {
				return UNKNOWN_TX_ID.with(txn.getId()).result();
			}

			do {
				var result = restore(serialization, data.getData(), TxHistoryEntry.class)
					.filter(txHistoryEntry -> sameTxId(txn, txHistoryEntry), IGNORED);

				if (result.isSuccess()) {
					return result;
				}

				status = readTxHistory(() -> cursor.getNext(key, data, null), data);
			}
			while (status == OperationStatus.SUCCESS);
		}
		return UNKNOWN_TX_ID.with(txn.getId()).result();
	}

	@Override
	public Result<List<TxHistoryEntry>> getTransactionHistory(REAddr addr, int size, Optional<Instant> ptr) {
		if (size <= 0) {
			return INVALID_PAGE_SIZE.with(size).result();
		}

		var instant = ptr.orElse(Instant.EPOCH);
		var key = asKey(addr, instant);
		var data = entry();

		try (var cursor = transactionHistory.openCursor(null, null)) {
			var status = readTxHistory(() -> cursor.getSearchKeyRange(key, data, null), data);
			if (status != OperationStatus.SUCCESS) {
				return Result.ok(List.of());
			}

			status = readTxHistory(() -> cursor.getLast(key, data, null), data);
			if (status != OperationStatus.SUCCESS) {
				return Result.ok(List.of());
			}

			// skip first entry if it's the same as the cursor
			if (instantFromKey(key).equals(instant)) {
				status = readTxHistory(() -> cursor.getPrev(key, data, null), data);

				if (status != OperationStatus.SUCCESS) {
					return Result.ok(List.of());
				}
			}

			var list = new ArrayList<TxHistoryEntry>();

			do {
				addrFromKey(key)
					.filter(addr::equals, Failure.failure(0, "Ignored"))
					.flatMap(__ -> restore(serialization, data.getData(), TxHistoryEntry.class))
					.onSuccess(list::add);

				status = readTxHistory(() -> cursor.getPrev(key, data, null), data);
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

	private boolean sameTxId(Txn txn, TxHistoryEntry txHistoryEntry) {
		return txHistoryEntry.getTxId().equals(txn.getId());
	}

	private Instant instantFromKey(DatabaseEntry key) {
		var buf = Unpooled.wrappedBuffer(key.getData(), key.getSize() - TIMESTAMP_SIZE, TIMESTAMP_SIZE);
		return Instant.ofEpochSecond(buf.readLong(), buf.readInt());
	}

	private Result<REAddr> addrFromKey(DatabaseEntry key) {
		var buf = Arrays.copyOf(key.getData(), ECPublicKey.COMPRESSED_BYTES + 1);
		return Result.wrap(INVALID_ACCOUNT_ADDRESS, () -> REAddr.of(buf));
	}

	private Result<Txn> retrieveTx(AID id) {
		return store.get(id)
			.map(Result::ok)
			.orElseGet(() -> UNKNOWN_TX_ID.with(id).result());
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

	private void open(boolean isTest) {
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

			if (System.getProperty("db.check_integrity", "1").equals("1")) {
				//TODO: Implement recovery, basically should be the same as fresh DB handling
			}

			// FIXME: removing the following for now in production
			// as it is double counting genesis transactions
			if (isTest) {
				if (addressBalances.count() == 0) {
					//Fresh DB, rebuild from log
					rebuildDatabase();
				}
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

		reTxn.getSignedBy().ifPresentOrElse(
			p -> reTxn.getActions().forEach(a -> storeAction(p, a)),
			() -> reTxn.getActions().forEach(a -> storeAction(null, a))
		);

		var addressesInActions = reTxn.getActions().stream()
			.map(REParsedAction::getTxAction)
			.flatMap(a -> {
				if (a instanceof TransferToken) {
					var transferToken = (TransferToken) a;
					return Stream.of(transferToken.from(), transferToken.to());
				} else if (a instanceof BurnToken) {
					var burnToken = (BurnToken) a;
					return Stream.of(burnToken.from());
				} else if (a instanceof MintToken) {
					var mintToken = (MintToken) a;
					return Stream.of(mintToken.to());
				} else if (a instanceof CreateFixedToken) {
					var createFixedToken = (CreateFixedToken) a;
					return Stream.of(createFixedToken.getAccountAddr());
				}

				return Stream.empty();
			});

		var addresses = Stream.concat(
			addressesInActions,
			reTxn.getSignedBy().stream().map(REAddr::ofPubKeyAccount)
		).collect(Collectors.toSet());

		transactionParser.parse(reTxn, currentTimestamp.get(), this::getRriOrFail)
			.onSuccess(parsed -> addresses.forEach(address -> storeSingleTransaction(parsed, address)));
	}

	private void storeAction(ECPublicKey user, REParsedAction action) {
		if (action.getTxAction() instanceof TransferToken) {
			var transferToken = (TransferToken) action.getTxAction();
			var rri = getRriOrFail(transferToken.resourceAddr());
			var entry0 = BalanceEntry.create(
				transferToken.from(),
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


		} else if (action.getTxAction() instanceof BurnToken) {
			var burnToken = (BurnToken) action.getTxAction();
			var rri = getRriOrFail(burnToken.resourceAddr());
			var entry0 = BalanceEntry.create(
				burnToken.from(),
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
		} else if (action.getTxAction() instanceof MintToken) {
			var mintToken = (MintToken) action.getTxAction();
			var rri = getRriOrFail(mintToken.resourceAddr());
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
		} else if (action.getTxAction() instanceof StakeTokens) {
			var stakeTokens = (StakeTokens) action.getTxAction();
			var rri = getRriOrFail(REAddr.ofNativeToken());
			var entry0 = BalanceEntry.create(
				stakeTokens.from(),
				stakeTokens.to(),
				rri,
				UInt384.from(stakeTokens.amount()),
				false
			);
			var entry1 = BalanceEntry.create(
				stakeTokens.from(),
				null,
				rri,
				UInt384.from(stakeTokens.amount()),
				true
			);
			storeBalanceEntry(entry0);
			storeBalanceEntry(entry1);
		} else if (action.getTxAction() instanceof UnstakeTokens) {
			var unstakeTokens = (UnstakeTokens) action.getTxAction();
			var rri = getRriOrFail(REAddr.ofNativeToken());
			var entry0 = BalanceEntry.create(
				unstakeTokens.accountAddr(),
				unstakeTokens.from(),
				rri,
				UInt384.from(unstakeTokens.amount()),
				true
			);
			var entry1 = BalanceEntry.create(
				unstakeTokens.accountAddr(),
				null,
				rri,
				UInt384.from(unstakeTokens.amount()),
				false
			);
			storeBalanceEntry(entry0);
			storeBalanceEntry(entry1);
		} else if (action.getTxAction() instanceof CreateMutableToken) {
			var createMutableToken = (CreateMutableToken) action.getTxAction();
			var record = TokenDefinitionRecord.from(user, createMutableToken);
			storeTokenDefinition(record);
		} else if (action.getTxAction() instanceof CreateFixedToken) {
			var createFixedToken = (CreateFixedToken) action.getTxAction();
			var record = TokenDefinitionRecord.from(createFixedToken);
			storeTokenDefinition(record);

			var entry0 = BalanceEntry.create(
				createFixedToken.getAccountAddr(),
				null,
				record.rri(),
				UInt384.from(createFixedToken.getSupply()),
				false
			);
			var entry1 = BalanceEntry.create(
				null,
				null,
				record.rri(),
				UInt384.from(createFixedToken.getSupply()),
				false
			);
			storeBalanceEntry(entry0);
			storeBalanceEntry(entry1);
		}
	}

	private void extractTimestamp(Stream<Particle> upParticles) {
		upParticles.filter(SystemParticle.class::isInstance)
			.map(SystemParticle.class::cast)
			.findFirst()
			.map(SystemParticle::asInstant)
			.ifPresent(currentTimestamp::set);
	}

	private Optional<ECPublicKey> extractCreator(Txn tx) {
		try {
			return constraintMachine.statelessVerify(tx).getSignedBy();
		} catch (RadixEngineException e) {
			throw new IllegalStateException();
		}
	}

	private void storeSingleTransaction(TxHistoryEntry txn, REAddr address) {
		var key = asKey(address, txn.timestamp());
		var data = serializeTo(entry(), txn);

		var status = withTime(
			() -> transactionHistory.put(null, key, data),
			() -> addTxHistoryWriteBytes(data),
			ELAPSED_APIDB_TRANSACTION_WRITE
		);

		if (status != OperationStatus.SUCCESS) {
			log.error("Error while storing transaction {} for {}", txn.getTxId(), address);
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
			log.error("Error {} while storing token definition {}", status, tokenDefinition.asJson());
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
		var address = buffer().writeBytes(balanceEntry.getOwner().getBytes());
		var buf = address.writeBytes(balanceEntry.rri().getBytes(StandardCharsets.UTF_8));

		if (balanceEntry.isStake()) {
			buf.writeBytes(balanceEntry.getDelegate().getBytes());
		} else {
			buf.writeZero(ECPublicKey.COMPRESSED_BYTES);
		}

		return entry(buf);
	}

	private static DatabaseEntry asKey(REAddr addr) {
		return entry(addr.getBytes());
	}

	private static DatabaseEntry asKey(REAddr addr, Instant timestamp) {
		return entry(buffer()
						 .writeBytes(addr.getBytes())
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
