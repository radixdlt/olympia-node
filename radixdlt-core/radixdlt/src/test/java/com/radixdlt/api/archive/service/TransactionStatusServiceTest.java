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
package com.radixdlt.client.service;

import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.parser.ParsedTxn;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.statecomputer.TxnsCommittedToLedger;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.utils.UInt256;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.radixdlt.client.api.TransactionStatus.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class TransactionStatusServiceTest {
	@Test
	public void transactionStatusIsStoredOnCommit() {
		var store = mock(BerkeleyLedgerEntryStore.class);

		var scheduledCacheCleanup = mockEventDispatcher();

		var transactionStatusService = new TransactionStatusService(
			store, scheduledCacheCleanup
		);

		var txn = randomTxn();
		var parsedTxn = new ParsedTxn(txn, UInt256.ZERO, null, null, null, false);
		var processedTxn = new REProcessedTxn(parsedTxn, null);
		var one = TxnsCommittedToLedger.create(List.of(processedTxn));
		transactionStatusService.atomsCommittedToLedgerEventProcessor().process(one);

		assertEquals(CONFIRMED, transactionStatusService.getTransactionStatus(txn.getId()));
	}

	@Test
	public void transactionStatusIsStoredOnReject() {
		var store = mock(BerkeleyLedgerEntryStore.class);

		var scheduledCacheCleanup = mockEventDispatcher();

		var transactionStatusService = new TransactionStatusService(
			store, scheduledCacheCleanup);

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
			store, scheduledCacheCleanup
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
			store, scheduledCacheCleanup
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
		var processedTxn = new REProcessedTxn(parsedTxn, null);
		var committed = TxnsCommittedToLedger.create(List.of(processedTxn));
		transactionStatusService.atomsCommittedToLedgerEventProcessor().process(committed);
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

	@SuppressWarnings("unchecked")
	private ScheduledEventDispatcher<ScheduledCacheCleanup> mockEventDispatcher() {
		return mock(ScheduledEventDispatcher.class);
	}

	private Txn randomTxn() {
		return Txn.create(HashUtils.random256().asBytes());
	}
}