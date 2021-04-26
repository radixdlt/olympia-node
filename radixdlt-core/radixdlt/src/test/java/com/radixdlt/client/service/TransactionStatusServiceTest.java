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

import org.junit.Test;

import com.radixdlt.atom.Txn;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.statecomputer.AtomsCommittedToLedger;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Consumer;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.client.api.TransactionStatus.CONFIRMED;
import static com.radixdlt.client.api.TransactionStatus.FAILED;
import static com.radixdlt.client.api.TransactionStatus.PENDING;
import static com.radixdlt.client.api.TransactionStatus.TRANSACTION_NOT_FOUND;

public class TransactionStatusServiceTest {

	@Test
	public void transactionStatusIsStoredOnCommit() {
		var store = mock(BerkeleyLedgerEntryStore.class);

		var rejected = mockObservable(MempoolAddFailure.class);
		var succeeded = mockObservable(MempoolAddSuccess.class);

		var scheduledCacheCleanup = mockEventDispatcher();

		var txn = randomTxn();
		var one = AtomsCommittedToLedger.create(List.of(txn), List.of());
		var committed = Observable.just(one);

		var transactionStatusService = new TransactionStatusService(
			store, committed, rejected, succeeded, scheduledCacheCleanup
		);

		assertEquals(CONFIRMED, transactionStatusService.getTransactionStatus(txn.getId()));
	}

	@Test
	public void transactionStatusIsStoredOnReject() {
		var store = mock(BerkeleyLedgerEntryStore.class);

		var committed = mockObservable(AtomsCommittedToLedger.class);
		var succeeded = mockObservable(MempoolAddSuccess.class);
		var scheduledCacheCleanup = mockEventDispatcher();

		var txn = randomTxn();
		var one = MempoolAddFailure.create(txn, null, null);
		var rejected = Observable.just(one);

		var transactionStatusService = new TransactionStatusService(
			store, committed, rejected, succeeded, scheduledCacheCleanup
		);

		assertEquals(FAILED, transactionStatusService.getTransactionStatus(txn.getId()));
	}

	@Test
	public void transactionStatusIsStoredOnSucceed() {
		var store = mock(BerkeleyLedgerEntryStore.class);

		var committed = mockObservable(AtomsCommittedToLedger.class);
		var rejected = mockObservable(MempoolAddFailure.class);
		var scheduledCacheCleanup = mockEventDispatcher();

		var txn = randomTxn();
		var one = MempoolAddSuccess.create(txn, null);
		var succeeded = Observable.just(one);

		var transactionStatusService = new TransactionStatusService(
			store, committed, rejected, succeeded, scheduledCacheCleanup
		);

		assertEquals(PENDING, transactionStatusService.getTransactionStatus(txn.getId()));
	}

	@Test
	public void onTimeoutAllEntriesAreRemovedExceptPendingOnes() {
		var store = mock(BerkeleyLedgerEntryStore.class);

		var scheduledCacheCleanup = mockEventDispatcher();

		var txnSucceeded = randomTxn();
		var succeeded = Observable.just(MempoolAddSuccess.create(txnSucceeded, null));
		var txnCommitted = randomTxn();
		var committed = Observable.just(AtomsCommittedToLedger.create(List.of(txnCommitted), List.of()));
		var txnRejected = randomTxn();
		var rejected = Observable.just(MempoolAddFailure.create(txnRejected, null, null));

		var start = Instant.now().minus(Duration.ofSeconds(10 * 60 + 1));
		var clockValues = List.of(start, start, start, start, start, start, Instant.now()).iterator();
		var counter = new AtomicInteger();

		var transactionStatusService = new TransactionStatusService(
			store, committed, rejected, succeeded, scheduledCacheCleanup
		) {
			@Override
			Instant clock() {
				System.out.println(counter.incrementAndGet());
				return clockValues.next();
			}
		};

		assertEquals(PENDING, transactionStatusService.getTransactionStatus(txnSucceeded.getId()));
		assertEquals(CONFIRMED, transactionStatusService.getTransactionStatus(txnCommitted.getId()));
		assertEquals(FAILED, transactionStatusService.getTransactionStatus(txnRejected.getId()));

		transactionStatusService.cacheCleanupProcessor().process(ScheduledCacheCleanup.create());

		assertEquals(PENDING, transactionStatusService.getTransactionStatus(txnSucceeded.getId()));
		assertEquals(TRANSACTION_NOT_FOUND, transactionStatusService.getTransactionStatus(txnCommitted.getId()));
		assertEquals(TRANSACTION_NOT_FOUND, transactionStatusService.getTransactionStatus(txnRejected.getId()));
	}

	@SuppressWarnings("unchecked")
	private ScheduledEventDispatcher<ScheduledCacheCleanup> mockEventDispatcher() {
		return mock(ScheduledEventDispatcher.class);
	}

	@SuppressWarnings("unchecked")
	private <T> Observable<T> mockObservable(Class<T> clazz) {
		var observable = mock(Observable.class);

		when(observable.subscribe(any(Consumer.class))).thenReturn(Disposable.disposed());

		return observable;
	}

	private Txn randomTxn() {
		return Txn.create(HashUtils.random256().asBytes());
	}
}