/* Copyright 2021 Radix DLT Ltd incorporated in England.
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
package com.radixdlt.api.service;

import com.google.common.collect.ImmutableClassToInstanceMap;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import org.junit.Assert;
import org.junit.Test;

import com.radixdlt.api.data.ActionEntry;
import com.radixdlt.api.data.TxHistoryEntry;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.parser.ParsedTxn;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.statecomputer.REOutput;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.api.data.TransactionStatus.CONFIRMED;
import static com.radixdlt.api.data.TransactionStatus.FAILED;
import static com.radixdlt.api.data.TransactionStatus.PENDING;
import static com.radixdlt.api.data.TransactionStatus.TRANSACTION_NOT_FOUND;

public class TransactionStatusServiceTest {
	private final ClientApiStore clientApiStore = mock(ClientApiStore.class);

	@Test
	public void transactionStatusIsStoredOnCommit() {
		var store = mock(BerkeleyLedgerEntryStore.class);

		var scheduledCacheCleanup = mockEventDispatcher();

		var transactionStatusService = new TransactionStatusService(
			store, scheduledCacheCleanup, clientApiStore
		);

		var txn = randomTxn();
		var parsedTxn = new ParsedTxn(txn, UInt256.ZERO, null, null, null, false);
		var processedTxn = new REProcessedTxn(parsedTxn, null, List.of());
		var one = REOutput.create(List.of(processedTxn));
		var update = new LedgerUpdate(mock(VerifiedTxnsAndProof.class), ImmutableClassToInstanceMap.of(REOutput.class, one));
		transactionStatusService.ledgerUpdateProcessor().process(update);

		assertEquals(CONFIRMED, transactionStatusService.getTransactionStatus(txn.getId()));
	}

	@Test
	public void transactionStatusIsStoredOnReject() {
		var store = mock(BerkeleyLedgerEntryStore.class);

		var scheduledCacheCleanup = mockEventDispatcher();

		var transactionStatusService = new TransactionStatusService(
			store, scheduledCacheCleanup, clientApiStore);

		var txn = randomTxn();
		var one = MempoolAddFailure.create(txn, null, null);
		transactionStatusService.mempoolAddFailureEventProcessor().process(one);

		assertEquals(FAILED, transactionStatusService.getTransactionStatus(txn.getId()));
	}

	@Test
	public void transactionStatusIsStoredOnSucceed() {
		var store = mock(BerkeleyLedgerEntryStore.class);

		var scheduledCacheCleanup = mockEventDispatcher();

		var txn = randomTxn();
		var one = MempoolAddSuccess.create(txn, null);

		var transactionStatusService = new TransactionStatusService(
			store, scheduledCacheCleanup, clientApiStore
		);

		transactionStatusService.mempoolAddSuccessEventProcessor().process(one);

		assertEquals(PENDING, transactionStatusService.getTransactionStatus(txn.getId()));
	}

	@Test
	public void onTimeoutAllEntriesAreRemovedExceptPendingOnes() {
		var store = mock(BerkeleyLedgerEntryStore.class);

		var scheduledCacheCleanup = mockEventDispatcher();

		var start = Instant.now().minus(Duration.ofSeconds(10 * 60 + 1));
		var clockValues = List.of(start, start, start, start, start, start, Instant.now()).iterator();
		var counter = new AtomicInteger();

		var transactionStatusService = new TransactionStatusService(
			store, scheduledCacheCleanup, clientApiStore
		) {
			@Override
			Instant clock() {
				System.out.println(counter.incrementAndGet());
				return clockValues.next();
			}
		};

		var txnSucceeded = randomTxn();
		var succeeded = MempoolAddSuccess.create(txnSucceeded, null);
		transactionStatusService.mempoolAddSuccessEventProcessor().process(succeeded);
		var txnCommitted = randomTxn();
		var parsedTxn = new ParsedTxn(txnCommitted, UInt256.ZERO, null, null, null, false);
		var processedTxn = new REProcessedTxn(parsedTxn, null, List.of());
		var committed = REOutput.create(List.of(processedTxn));
		var update = new LedgerUpdate(mock(VerifiedTxnsAndProof.class), ImmutableClassToInstanceMap.of(REOutput.class, committed));
		transactionStatusService.ledgerUpdateProcessor().process(update);
		var txnRejected = randomTxn();
		var rejected = MempoolAddFailure.create(txnRejected, null, null);
		transactionStatusService.mempoolAddFailureEventProcessor().process(rejected);

		assertEquals(PENDING, transactionStatusService.getTransactionStatus(txnSucceeded.getId()));
		assertEquals(CONFIRMED, transactionStatusService.getTransactionStatus(txnCommitted.getId()));
		assertEquals(FAILED, transactionStatusService.getTransactionStatus(txnRejected.getId()));

		transactionStatusService.cacheCleanupEventProcessor().process(ScheduledCacheCleanup.create());

		assertEquals(PENDING, transactionStatusService.getTransactionStatus(txnSucceeded.getId()));
		assertEquals(TRANSACTION_NOT_FOUND, transactionStatusService.getTransactionStatus(txnCommitted.getId()));
		assertEquals(TRANSACTION_NOT_FOUND, transactionStatusService.getTransactionStatus(txnRejected.getId()));
	}

	@Test
	public void testGetTransaction() {
		var entry = createTxHistoryEntry(AID.ZERO);
		var store = mock(BerkeleyLedgerEntryStore.class);

		var scheduledCacheCleanup = mockEventDispatcher();

		var transactionStatusService = new TransactionStatusService(
			store, scheduledCacheCleanup, clientApiStore
		);

		when(clientApiStore.getTransaction(AID.ZERO))
			.thenReturn(Result.ok(entry));

		transactionStatusService.getTransaction(entry.getTxId())
			.onSuccess(result -> assertEquals(entry, result))
			.onFailureDo(Assert::fail);
	}

	private TxHistoryEntry createTxHistoryEntry(AID txId) {
		var now = Instant.ofEpochMilli(Instant.now().toEpochMilli());
		var action = ActionEntry.unknown();
		return TxHistoryEntry.create(txId, now, UInt256.ONE, "text", List.of(action));
	}

	@SuppressWarnings("unchecked")
	private ScheduledEventDispatcher<ScheduledCacheCleanup> mockEventDispatcher() {
		return mock(ScheduledEventDispatcher.class);
	}

	private Txn randomTxn() {
		return Txn.create(HashUtils.random256().asBytes());
	}
}