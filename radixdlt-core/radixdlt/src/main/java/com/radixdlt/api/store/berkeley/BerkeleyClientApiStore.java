/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.api.store.berkeley;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Streams;
import com.google.inject.Inject;
import com.radixdlt.accounting.REResourceAccounting;
import com.radixdlt.accounting.TwoActorEntry;
import com.radixdlt.api.data.ActionEntry;
import com.radixdlt.api.data.BalanceEntry;
import com.radixdlt.api.data.ScheduledQueueFlush;
import com.radixdlt.api.data.TxHistoryEntry;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.api.store.ClientApiStoreException;
import com.radixdlt.api.store.TokenDefinitionRecord;
import com.radixdlt.api.store.TransactionParser;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.system.state.RoundData;
import com.radixdlt.application.tokens.Bucket;
import com.radixdlt.application.tokens.ResourceCreatedEvent;
import com.radixdlt.constraintmachine.REEvent;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.networks.Addressing;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.REOutput;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.utils.UInt384;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.OperationStatus;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import static com.google.common.primitives.UnsignedBytes.lexicographicalComparator;
import static com.radixdlt.api.data.ApiErrors.INVALID_PAGE_SIZE;
import static com.radixdlt.api.data.ApiErrors.SYMBOL_DOES_NOT_MATCH;
import static com.radixdlt.api.data.ApiErrors.UNKNOWN_ACCOUNT_ADDRESS;
import static com.radixdlt.api.data.ApiErrors.UNKNOWN_RRI;
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
import static com.radixdlt.utils.functional.Result.wrap;

public final class BerkeleyClientApiStore implements ClientApiStore {
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
	private final Serialization serialization;
	private final SystemCounters systemCounters;
	private final ScheduledEventDispatcher<ScheduledQueueFlush> scheduledFlushEventDispatcher;
	private final StackingCollector<REOutput> txCollector = StackingCollector.create();
	private final AtomicReference<Instant> currentTimestamp = new AtomicReference<>(NOW);
	private final AtomicLong currentEpoch = new AtomicLong(0);
	private final AtomicLong currentRound = new AtomicLong(0);
	private final TransactionParser transactionParser;
	private final Addressing addressing;
	private final Forks forks;

	private Database transactionHistory;
	private Database tokenDefinitions;
	private Database addressBalances;
	private Database supplyBalances;

	private final Cache<REAddr, String> rriCache = CacheBuilder.newBuilder()
		.maximumSize(1024)
		.build();

	public BerkeleyClientApiStore(
		DatabaseEnvironment dbEnv,
		Serialization serialization,
		SystemCounters systemCounters,
		ScheduledEventDispatcher<ScheduledQueueFlush> scheduledFlushEventDispatcher,
		TransactionParser transactionParser,
		boolean isTest,
		Addressing addressing,
		Forks forks
	) {
		this.dbEnv = dbEnv;
		this.serialization = serialization;
		this.systemCounters = systemCounters;
		this.scheduledFlushEventDispatcher = scheduledFlushEventDispatcher;
		this.transactionParser = transactionParser;
		this.addressing = addressing;
		this.forks = forks;

		open(isTest);
	}

	@Inject
	public BerkeleyClientApiStore(
		DatabaseEnvironment dbEnv,
		Serialization serialization,
		SystemCounters systemCounters,
		ScheduledEventDispatcher<ScheduledQueueFlush> scheduledFlushEventDispatcher,
		TransactionParser transactionParser,
		Addressing addressing,
		Forks forks
	) {
		this(dbEnv, serialization, systemCounters,
			 scheduledFlushEventDispatcher, transactionParser, false, addressing, forks
		);
	}

	@Override
	public Result<REAddr> parseRri(String rri) {
		return addressing.forResources().parseFunctional(rri)
			.flatMap(tuple -> tuple.map(
				(symbol, address) -> getTokenDefinition(address)
					.map(TokenDefinitionRecord::getSymbol)
					.filter(symbol::equals, SYMBOL_DOES_NOT_MATCH)
					.map(__ -> address)
			));
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
			entry.getEpochUnlocked(),
			entry.getTxId()
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
					.filter(entry -> type != BalanceType.SPENDABLE || !entry.getAmount().isZero())
					.map(entry -> entry.rri().equals("stake-ownership") ? computeStakeEntry(entry) : entry)
					.ifPresent(list::add);


				status = readBalance(() -> cursor.getNext(key, data, null), data);
			} while (status == OperationStatus.SUCCESS);

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
			return rriCache.get(addr, () -> getTokenDefinition(addr).toOptional().orElseThrow().rri(addressing));
		} catch (ExecutionException e) {
			log.error("Unable to find rri of token at address {}", addr);
			throw new IllegalStateException(e);
		}
	}

	private void storeCollected() {
		var count = withTime(
			() -> txCollector.consumeCollected(this::storeTransactionBatch),
			() -> systemCounters.increment(COUNT_APIDB_FLUSH_COUNT),
			ELAPSED_APIDB_FLUSH_TIME
		);

		systemCounters.add(COUNT_APIDB_QUEUE_SIZE, -count);
	}

	@Override
	public Result<List<TxHistoryEntry>> getTransactionHistory(REAddr addr, int size, Optional<Instant> ptr, boolean verbose) {
		if (size <= 0) {
			return INVALID_PAGE_SIZE.with(size).result();
		}

		var instant = ptr.orElse(Instant.MAX);
		var key = asTxnHistoryKey(addr, instant);
		var data = entry();

		try (var cursor = transactionHistory.openCursor(null, null)) {
			var status = readTxHistory(() -> cursor.getSearchKeyRange(key, data, null), data);

			//When searching with no cursor, exact navigation (cursor.getSearchKey) may fail,
			//because there is no exact match. Nevertheless, cursor is positioned to correct location,
			//so we just need get previous record.
			if (status != OperationStatus.SUCCESS) {
				log.debug("Skipping first record");

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
			var rangeStarted = false;
			var skipped = 0;

			do {
				var reAddr = addrFromKey(key).fold(__ -> REAddr.ofSystem(), v -> v);

				if (reAddr.equals(addr)) {
					restore(serialization, data.getData(), TxHistoryEntry.class)
						.flatMap(entry -> filterOtherActions(entry, verbose))
						.onSuccess(list::add);

					rangeStarted = true;
				} else {
					if (rangeStarted) {
						break;
					} else {
						skipped++;
					}
				}

				status = readTxHistory(() -> cursor.getPrev(key, data, null), data);
			}
			while (status == OperationStatus.SUCCESS && list.size() < size);

			if (skipped > 1) {
				log.debug("Skipped {} records for cursor {}", skipped, ptr);
			}

			return Result.ok(list);
		}
	}

	private Result<TxHistoryEntry> filterOtherActions(TxHistoryEntry entry, boolean verbose) {
		if (verbose) {
			return Result.ok(entry);
		}

		var filteredActions = entry.getActions().stream()
			.filter(ActionEntry::isKnown)
			.collect(Collectors.toList());

		return filteredActions.isEmpty()
			   ? Result.fail(Failure.irrelevant())
			   : Result.ok(entry.withActions(filteredActions));
	}

	@Override
	public EventProcessor<ScheduledQueueFlush> queueFlushProcessor() {
		return flush -> {
			storeCollected();
			scheduledFlushEventDispatcher.dispatch(ScheduledQueueFlush.create(), DEFAULT_FLUSH_INTERVAL);
		};
	}

	public void close() {
		storeCollected();
		closeAll();
	}

	private Instant instantFromKey(DatabaseEntry key) {
		var buf = Unpooled.wrappedBuffer(key.getData(), key.getSize() - TIMESTAMP_SIZE, TIMESTAMP_SIZE);
		return Instant.ofEpochSecond(buf.readLong(), buf.readInt());
	}

	private Result<REAddr> addrFromKey(DatabaseEntry key) {
		var buf = Arrays.copyOf(key.getData(), ECPublicKey.COMPRESSED_BYTES + 1);
		return wrap(INVALID_ACCOUNT_ADDRESS, () -> REAddr.of(buf));
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
			systemCounters.add(elapsedCounter, elapsed);
			postAction.run();
		}
	}

	private void open(boolean isTest) {
		try {
			// This SuppressWarnings here is valid, as ownership of the underlying
			// resource is not changed here, the resource is just accessed.
			openAll();

			if (System.getProperty("db.check_integrity", "1").equals("1")) {
				//TODO: Implement recovery, basically should be the same as fresh DB handling
			}

			scheduledFlushEventDispatcher.dispatch(ScheduledQueueFlush.create(), DEFAULT_FLUSH_INTERVAL);
			log.info("Client API Store opened");
		} catch (Exception e) {
			throw new ClientApiStoreException("Error while opening databases", e);
		}
	}

	private void openAll() {
		@SuppressWarnings("resource")
		var env = dbEnv.getEnvironment();
		var uniqueConfig = createUniqueConfig();

		addressBalances = env.openDatabase(null, ADDRESS_BALANCE_DB, uniqueConfig);
		supplyBalances = env.openDatabase(null, SUPPLY_BALANCE_DB, uniqueConfig);
		tokenDefinitions = env.openDatabase(null, TOKEN_DEFINITION_DB, uniqueConfig);
		transactionHistory = env.openDatabase(null, EXECUTED_TRANSACTIONS_DB, uniqueConfig);
	}

	private void closeAll() {
		safeClose(transactionHistory);
		safeClose(tokenDefinitions);
		safeClose(addressBalances);
		safeClose(supplyBalances);
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

	@Override
	public EventProcessor<REOutput> atomsCommittedToLedgerEventProcessor() {
		return this::newBatch;
	}

	@Override
	public EventProcessor<LedgerUpdate> ledgerUpdateProcessor() {
		return u -> {
			var output = u.getStateComputerOutput().getInstance(REOutput.class);
			if (output != null) {
				newBatch(output);
			}
		};
	}

	private void newBatch(REOutput transactions) {
		txCollector.push(transactions);
		systemCounters.increment(COUNT_APIDB_QUEUE_SIZE);
	}

	private void storeTransactionBatch(REOutput act) {
		act.getProcessedTxns().forEach(this::processRETransaction);
	}

	private JSONObject accountingJson(
		long epoch,
		REProcessedTxn reTxn,
		List<REResourceAccounting> accountingObjects
	) {
		var txnJson = new JSONObject();
		txnJson.put("transaction_identifier", reTxn.getTxn().getId().toString());
		txnJson.put("epoch", epoch);
		if (currentRound.get() != 0L) {
			txnJson.put("round", currentRound.get());
		}
		txnJson.put("epoch", epoch);
		txnJson.put("timestamp", currentTimestamp.get().toEpochMilli());
		txnJson.put("type", reTxn.isSystemOnly() ? "SYSTEM" : "USER");
		txnJson.put("transaction_size", reTxn.getTxn().getPayload().length);
		var accountingEntries = new JSONArray();
		txnJson.put("accounting_entries", accountingEntries);
		accountingObjects.forEach(accounting -> {
			if (accounting.bucketAccounting().isEmpty()) {
				return;
			}

			var entry = new JSONObject();
			var bucketAccounting = new JSONArray();
			var isFee = TwoActorEntry.parse(accounting.bucketAccounting()).map(TwoActorEntry::isFee).orElse(false);
			entry.put("isFee", isFee);

			accounting.bucketAccounting().forEach((b, i) -> {
				var bucketJson = new JSONObject();
				bucketJson.put("type", b.getClass().getSimpleName());
				if (b.getOwner() != null) {
					bucketJson.put("owner", addressing.forAccounts().of(b.getOwner()));
				}
				if (b.getValidatorKey() != null) {
					bucketJson.put("validator", addressing.forValidators().of(b.getValidatorKey()));
				}
				bucketJson.put("delta", i.toString());
				bucketJson.put("asset", b.resourceAddr() == null ? "stake_ownership" : getRriOrFail(b.resourceAddr()));
				bucketAccounting.put(bucketJson);
			});
			entry.put("entries", bucketAccounting);
			accountingEntries.put(entry);
		});
		return txnJson;
	}

	private void processRETransaction(REProcessedTxn reTxn) {
		// TODO: cur epoch retrieval a bit hacky but needs to be like this for now
		// TODO: as epoch get updated at the end of an epoch transition
		var curEpoch = currentEpoch.get();
		processEvents(reTxn.getEvents());

		var accountingObjects = reTxn.getGroupedStateUpdates().stream()
			.map(updates -> processGroupedStateUpdates(updates, reTxn.getTxn().getId()))
			.collect(Collectors.toList());

		var actions = accountingObjects.stream()
			.map(a -> TwoActorEntry.parse(a.bucketAccounting()))
			.collect(Collectors.toList());
		var addressesInTxn =
			accountingObjects.stream()
				.flatMap(r -> r.bucketAccounting().keySet().stream())
				.map(Bucket::getOwner)
				.filter(Objects::nonNull);
		var addresses = Stream.concat(
			addressesInTxn,
			reTxn.getSignedBy().stream().map(REAddr::ofPubKeyAccount)
		).collect(Collectors.toSet());

		var entry = transactionParser.parse(
			reTxn,
			actions,
			currentTimestamp.get(),
			this::getRriOrFail,
			this::computeStakeFromOwnership
		);
		addresses.forEach(address -> storeSingleTransaction(entry, address));

		log.debug("TRANSACTION_LOG: {}", () -> accountingJson(curEpoch, reTxn, accountingObjects));
	}

	private void processEvents(List<REEvent> events) {
		for (var event : events) {
			if (event instanceof ResourceCreatedEvent) {
				var resourceCreated = (ResourceCreatedEvent) event;
				var record = TokenDefinitionRecord.create(
					resourceCreated.getSymbol(),
					resourceCreated.getMetadata().getName(),
					resourceCreated.getMetadata().getAddr(),
					resourceCreated.getMetadata().getDescription(),
					UInt384.ZERO,
					resourceCreated.getMetadata().getIconUrl(),
					resourceCreated.getMetadata().getUrl(),
					resourceCreated.getTokenResource().isMutable()
				);
				storeTokenDefinition(record);
			}
		}
	}

	private REResourceAccounting processGroupedStateUpdates(List<REStateUpdate> updates, AID txId) {
		var curEpoch = currentEpoch.get();
		for (var update : updates) {
			var substate = update.getParsed();
			if (substate instanceof RoundData) {
				var d = (RoundData) substate;
				if (d.getTimestamp() > 0) {
					currentTimestamp.set(d.asInstant());
				}
				currentRound.set(d.getView());
			} else if (substate instanceof EpochData) {
				var d = (EpochData) substate;
				currentEpoch.set(d.getEpoch());
			}
		}

		var rules = forks.get(curEpoch);

		var accounting = REResourceAccounting.compute(updates.stream());
		var bucketAccounting = accounting.bucketAccounting();
		var bucketEntries = bucketAccounting.entrySet().stream()
			.map(e -> {
				var r = e.getKey();
				var i = e.getValue();
				var rri = r.resourceAddr() != null ? getRriOrFail(r.resourceAddr()) : "stake-ownership";
				var epochUnlock = r.getEpochUnlock();
				var entry = BalanceEntry.create(
					r.getOwner(),
					r.getValidatorKey(),
					rri,
					UInt384.from(i.abs().toByteArray()),
					i.signum() == -1,
					(epochUnlock != null && epochUnlock == 0)
						? (Long) (curEpoch + 1 + rules.getConfig().getUnstakingEpochDelay())
						: epochUnlock,
					txId
				);
				return entry;
			});
		var stakeOwnershipEntries = accounting.stakeOwnershipAccounting().entrySet().stream()
			.filter(e -> !e.getValue().equals(BigInteger.ZERO))
			.map(e -> BalanceEntry.create(
				null,
				e.getKey(),
				"stake-ownership",
				UInt384.from(e.getValue().abs().toByteArray()),
				e.getValue().signum() == -1,
				null,
				txId
			));
		var resourceEntries = accounting.resourceAccounting().entrySet().stream()
			.filter(e -> !e.getValue().equals(BigInteger.ZERO))
			.map(e -> {
				var rri = getRriOrFail(e.getKey());
				var amt = UInt384.from(e.getValue().abs().toByteArray());
				var isNegative = e.getValue().signum() == -1;
				return BalanceEntry.resource(rri, amt, isNegative);
			});
		Streams.concat(bucketEntries, stakeOwnershipEntries, resourceEntries)
			.forEach(this::storeBalanceEntry);

		return accounting;
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
			log.error("Error {} while storing token definition {}", status, tokenDefinition.asJson(addressing));
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
