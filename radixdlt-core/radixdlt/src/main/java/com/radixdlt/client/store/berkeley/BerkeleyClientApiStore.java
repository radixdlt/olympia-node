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

import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.UnstakeOwnership;
import com.radixdlt.atommodel.system.state.EpochData;
import com.radixdlt.atommodel.system.state.RoundData;
import com.radixdlt.atommodel.system.state.StakeOwnership;
import com.radixdlt.atommodel.system.state.ValidatorStake;
import com.radixdlt.atommodel.tokens.state.ExittingStake;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.atommodel.tokens.state.TokenResource;
import com.radixdlt.atommodel.tokens.state.TokensInAccount;
import com.radixdlt.atomos.REAddrParticle;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.constraintmachine.TxnParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.api.construction.TxnParser;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.client.Rri;
import com.radixdlt.client.api.TxHistoryEntry;
import com.radixdlt.client.store.ClientApiStore;
import com.radixdlt.client.store.ClientApiStoreException;
import com.radixdlt.client.store.TokenDefinitionRecord;
import com.radixdlt.client.store.TransactionParser;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.REParsedAction;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.TxnsCommittedToLedger;
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

@Singleton
public class BerkeleyClientApiStore implements ClientApiStore {
	private static final Logger log = LogManager.getLogger();

	private static final String EXECUTED_TRANSACTIONS_DB = "radix.executed_transactions_db";
	private static final String ADDRESS_BALANCE_DB = "radix.address.balance_db";
	private static final String SUPPLY_BALANCE_DB = "radix.supply.balance_db";
	private static final String TOKEN_DEFINITION_DB = "radix.token_definition_db";
	private static final long DEFAULT_FLUSH_INTERVAL = 250L;
	private static final int KEY_BUFFER_INITIAL_CAPACITY = 1024;
	private static final int TIMESTAMP_SIZE = Long.BYTES + Integer.BYTES;
	private static final Instant NOW = Instant.ofEpochMilli(Instant.now().toEpochMilli());
	private static final Failure IGNORED = Failure.failure(0, "Ignored");

	private final DatabaseEnvironment dbEnv;
	private final BerkeleyLedgerEntryStore store;
	private final Serialization serialization;
	private final SystemCounters systemCounters;
	private final ScheduledEventDispatcher<ScheduledQueueFlush> scheduledFlushEventDispatcher;
	private final StackingCollector<TxnsCommittedToLedger> txCollector = StackingCollector.create();
	private final CompositeDisposable disposable = new CompositeDisposable();
	private final AtomicReference<Instant> currentTimestamp = new AtomicReference<>(NOW);
	private final AtomicLong currentEpoch = new AtomicLong(0);
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
		TransactionParser transactionParser
	) {
		this.dbEnv = dbEnv;
		this.constraintMachine = constraintMachine;
		this.txnParser = txnParser;
		this.store = store;
		this.serialization = serialization;
		this.systemCounters = systemCounters;
		this.scheduledFlushEventDispatcher = scheduledFlushEventDispatcher;
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

	private UInt384 computeStakeFromOwnership(ECPublicKey delegateKey, UInt384 ownership) {
		var key = asAddrBalanceValidatorStakeKey(delegateKey);
		var data = entry();
		var status = addressBalances.get(null, key, data, null);
		if (status == OperationStatus.NOTFOUND) {
			// For pre-betanet3
			return ownership;
		}
		var totalStake = restore(serialization, data.getData(), BalanceEntry.class).toOptional().orElseThrow();

		var key2 = asAddrBalanceValidatorStakeOwnership(delegateKey);
		var data2 = entry();
		addressBalances.get(null, key2, data2, null);
		var totalOwnership = restore(serialization, data2.getData(), BalanceEntry.class).toOptional().orElseThrow();
		return totalStake.getAmount().multiply(ownership).divide(totalOwnership.getAmount());
	}

	private BalanceEntry computeStakeEntry(BalanceEntry entry) {
		return BalanceEntry.create(
			entry.getOwner(),
			entry.getDelegate(),
			getRriOrFail(REAddr.ofNativeToken()),
			computeStakeFromOwnership(entry.getDelegate(), entry.getAmount()),
			false,
			entry.getEpochUnlocked()
		);
	}

	public long getEpoch() {
		return currentEpoch.get();
	}

	@Override
	public Result<List<BalanceEntry>> getTokenBalances(REAddr addr, BalanceType type) {
		try (var cursor = addressBalances.openCursor(null, null)) {
			var key = asAddrBalanceKey(addr);
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
					.filter(entry -> entry.getType().equals(type))
					.filter(entry -> entry.getOwner().equals(addr))
					.map(entry -> entry.rri().equals("stake-ownership") ? computeStakeEntry(entry) : entry)
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
			var key = asAddrBalanceKey(addr);
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
		var count = withTime(
			() -> txCollector.consumeCollected(this::storeTransactionBatch),
			() -> systemCounters.increment(COUNT_APIDB_FLUSH_COUNT),
			ELAPSED_APIDB_FLUSH_TIME
		);

		systemCounters.add(COUNT_APIDB_QUEUE_SIZE, -count);
	}

	private Result<TxHistoryEntry> lookupTransactionInHistory(REAddr addr, Txn txn) {
		var key = asTxnHistoryKey(addr, Instant.EPOCH);
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
		var key = asTxnHistoryKey(addr, instant);
		var data = entry();

		try (var cursor = transactionHistory.openCursor(null, null)) {
			var status = readTxHistory(() -> cursor.getSearchKey(key, data, null), data);

			//When searching with no cursor, exact navigation (cursor.getSearchKey) may fail,
			//because there is no exact match. Nevertheless, cursor is positioned to correct location,
			//so we just need get previous record.
			if (status != OperationStatus.SUCCESS) {
				status = readTxHistory(() -> cursor.getPrev(key, data, null), data);

				if (status != OperationStatus.SUCCESS) {
					return Result.ok(List.of());
				}
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

	public EventProcessor<TxnsCommittedToLedger> atomsCommittedToLedgerEventProcessor() {
		return this::newBatch;
	}

	private void newBatch(TxnsCommittedToLedger transactions) {
		txCollector.push(transactions);
		systemCounters.increment(COUNT_APIDB_QUEUE_SIZE);
	}

	private void storeTransactionBatch(TxnsCommittedToLedger act) {
		act.getParsedTxs().forEach(this::processRETransaction);
	}

	private void processRETransaction(REProcessedTxn reTxn) {
		reTxn.getGroupedStateUpdates().forEach(this::updateSubstate);

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
				} else if (a instanceof StakeTokens) {
					var stakeTokens = (StakeTokens) a;
					return Stream.of(stakeTokens.from());
				} else if (a instanceof UnstakeOwnership) {
					var unstake = (UnstakeOwnership) a;
					return Stream.of(unstake.accountAddr());
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

		transactionParser.parse(reTxn, currentTimestamp.get(), this::getRriOrFail, this::computeStakeFromOwnership)
			.onSuccess(parsed -> addresses.forEach(address -> storeSingleTransaction(parsed, address)));
	}

	private void updateSubstate(List<REStateUpdate> updates) {
		byte[] addressArg = null;
		for (var update : updates) {
			var substate = update.getRawSubstate();
			if (substate instanceof TokensInAccount) {
				var tokensInAccount = (TokensInAccount) substate;
				var rri = getRriOrFail(tokensInAccount.getResourceAddr());
				var accountEntry = BalanceEntry.create(
					tokensInAccount.getHoldingAddr(),
					null,
					rri,
					UInt384.from(tokensInAccount.getAmount()),
					update.isShutDown(),
					null
				);
				storeBalanceEntry(accountEntry);

				// Can probably optimize this out for every transfer
				var tokenEntry = BalanceEntry.create(
					null,
					null,
					rri,
					UInt384.from(tokensInAccount.getAmount()),
					update.isShutDown(),
					null
				);
				storeBalanceEntry(tokenEntry);
			} else if (substate instanceof PreparedStake) {
				var preparedStake = (PreparedStake) substate;
				var rri = getRriOrFail(REAddr.ofNativeToken());
				var accountEntry = BalanceEntry.create(
					preparedStake.getOwner(),
					preparedStake.getDelegateKey(),
					rri,
					UInt384.from(preparedStake.getAmount()),
					update.isShutDown(),
					null
				);
				storeBalanceEntry(accountEntry);

				// Can probably optimize this out for every transfer
				var tokenEntry = BalanceEntry.create(
					null,
					null,
					rri,
					UInt384.from(preparedStake.getAmount()),
					update.isShutDown(),
					null
				);
				storeBalanceEntry(tokenEntry);
			} else if (substate instanceof PreparedUnstakeOwnership) {
				var unstakeOwnership = (PreparedUnstakeOwnership) substate;
				var accountEntry = BalanceEntry.create(
					unstakeOwnership.getOwner(),
					unstakeOwnership.getDelegateKey(),
					"stake-ownership",
					UInt384.from(unstakeOwnership.getAmount()),
					update.isShutDown(),
					getEpoch() + ValidatorStake.EPOCHS_LOCKED
				);
				storeBalanceEntry(accountEntry);

			} else if (substate instanceof ExittingStake) {
				var exittingStake = (ExittingStake) substate;
				var rri = getRriOrFail(REAddr.ofNativeToken());

				var accountEntry = BalanceEntry.create(
					exittingStake.getOwner(),
					exittingStake.getDelegateKey(),
					rri,
					UInt384.from(exittingStake.getAmount()),
					update.isShutDown(),
					exittingStake.getEpochUnlocked()
				);
				storeBalanceEntry(accountEntry);

				// Token balance
				var tokenEntry = BalanceEntry.create(
					null,
					null,
					rri,
					UInt384.from(exittingStake.getAmount()),
					update.isShutDown(),
					null
				);
				storeBalanceEntry(tokenEntry);
			} else if (substate instanceof StakeOwnership) {
				var stakeOwnership = (StakeOwnership) substate;
				var accountEntry = BalanceEntry.create(
					stakeOwnership.getOwner(),
					stakeOwnership.getDelegateKey(),
					"stake-ownership",
					UInt384.from(stakeOwnership.getAmount()),
					update.isShutDown(),
					null
				);
				storeBalanceEntry(accountEntry);
			} else if (substate instanceof ValidatorStake) {
				var validatorStake = (ValidatorStake) substate;
				var rri = getRriOrFail(REAddr.ofNativeToken());

				// validator ownership
				var ownershipEntry = BalanceEntry.create(
					null,
					validatorStake.getValidatorKey(),
					"stake-ownership",
					UInt384.from(validatorStake.getTotalOwnership()),
					update.isShutDown(),
					null
				);
				storeBalanceEntry(ownershipEntry);

				// validator stake
				var validatorEntry = BalanceEntry.create(
					null,
					validatorStake.getValidatorKey(),
					rri,
					UInt384.from(validatorStake.getTotalStake()),
					update.isShutDown(),
					null
				);
				storeBalanceEntry(validatorEntry);

				var tokenEntry = BalanceEntry.create(
					null,
					null,
					rri,
					UInt384.from(validatorStake.getTotalStake()),
					update.isShutDown(),
					null
				);
				storeBalanceEntry(tokenEntry);
			} else if (substate instanceof REAddrParticle) {
				// FIXME: sort of a hacky way of getting this info
				addressArg = update.getArg().orElse("xrd".getBytes(StandardCharsets.UTF_8));
			} else if (substate instanceof TokenResource) {
				var tokenResource = (TokenResource) substate;
				if (addressArg == null) {
					throw new IllegalStateException();
				}
				var symbol = new String(addressArg);
				var record = TokenDefinitionRecord.create(
					symbol,
					tokenResource.getName(),
					tokenResource.getAddr(),
					tokenResource.getDescription(),
					UInt384.ZERO,
					tokenResource.getIconUrl(),
					tokenResource.getUrl(),
					tokenResource.isMutable()
				);
				storeTokenDefinition(record);
			} else if (substate instanceof SystemParticle && update.isBootUp()) {
				var s = (SystemParticle) substate;
				currentTimestamp.set(s.asInstant());
				currentEpoch.set(s.getEpoch());
			} else if (substate instanceof RoundData && update.isBootUp()) {
				var d = (RoundData) substate;
				currentTimestamp.set(d.asInstant());
			} else if (substate instanceof EpochData && update.isBootUp()) {
				var d = (EpochData) substate;
				currentEpoch.set(d.getEpoch());
			}
		}
	}

	private Optional<ECPublicKey> extractCreator(Txn tx) {
		try {
			return constraintMachine.parse(tx).getSignedBy();
		} catch (TxnParseException e) {
			throw new IllegalStateException();
		}
	}

	private void storeSingleTransaction(TxHistoryEntry txn, REAddr address) {
		var key = asTxnHistoryKey(address, txn.timestamp());
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
		var key = asAddrBalanceKey(tokenDefinition.addr());
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
		var key = entry.isSupply() ? asKey(entry.rri()) : asAddrBalanceKey(entry);
		mergeBalances(key, entry(), entry, entry.isUnstake() || entry.isStake());
	}

	private void mergeBalances(DatabaseEntry key, DatabaseEntry value, BalanceEntry balanceEntry, boolean deleteIfZero) {
		var database = balanceEntry.isSupply() ? supplyBalances : addressBalances;
		var oldValue = entry();
		var status = readBalance(() -> database.get(null, key, oldValue, null), oldValue);

		if (status == OperationStatus.NOTFOUND) {
			serializeTo(value, balanceEntry);
		} else if (status == OperationStatus.SUCCESS) {
			// Merge with existing balance
			restore(serialization, oldValue.getData(), BalanceEntry.class)
				.map(existingBalance -> existingBalance.add(balanceEntry))
				.onSuccess(entry -> {
					if (deleteIfZero && entry.getAmount().isZero()) {
						value.setData(null);
					} else {
						serializeTo(value, entry);
					}
				})
				.onFailure(this::reportError);
		}

		if (value.getData() == null) {
			status = database.delete(null, key);
		} else {
			status = writeBalance(() -> database.put(null, key, value), value);
		}

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

	private static DatabaseEntry asAddrBalanceKey(BalanceEntry balanceEntry) {
		var buf = buffer();
		if (balanceEntry.getOwner() != null) {
			buf.writeBytes(balanceEntry.getOwner().getBytes());
		} else {
			buf.writeZero(ECPublicKey.COMPRESSED_BYTES + 1);
		}

		buf.writeBytes(balanceEntry.rri().getBytes(StandardCharsets.UTF_8));

		if (balanceEntry.isStake() || balanceEntry.isUnstake()) {
			buf.writeBytes(balanceEntry.getDelegate().getBytes());
		} else {
			buf.writeZero(ECPublicKey.COMPRESSED_BYTES);
		}

		if (balanceEntry.isUnstake()) {
			buf.writeLong(balanceEntry.getEpochUnlocked());
		} else {
			buf.writeZero(Long.BYTES);
		}

		return entry(buf);
	}

	private static DatabaseEntry asAddrBalanceKey(REAddr addr) {
		return entry(addr.getBytes());
	}

	private DatabaseEntry asAddrBalanceValidatorStakeKey(ECPublicKey validatorKey) {
		var buf = buffer();
		buf.writeZero(ECPublicKey.COMPRESSED_BYTES + 1);
		var rri = getRriOrFail(REAddr.ofNativeToken());
		buf.writeBytes(rri.getBytes(StandardCharsets.UTF_8));
		buf.writeBytes(validatorKey.getBytes());
		buf.writeZero(Long.BYTES);
		return entry(buf);
	}

	private DatabaseEntry asAddrBalanceValidatorStakeOwnership(ECPublicKey validatorKey) {
		var buf = buffer();
		buf.writeZero(ECPublicKey.COMPRESSED_BYTES + 1);
		buf.writeBytes("stake-ownership".getBytes(StandardCharsets.UTF_8));
		buf.writeBytes(validatorKey.getBytes());
		buf.writeZero(Long.BYTES);
		return entry(buf);
	}

	private static DatabaseEntry asTxnHistoryKey(REAddr addr, Instant timestamp) {
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
