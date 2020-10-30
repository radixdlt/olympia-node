/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.sync;

import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.LedgerUpdateProcessor;
import com.radixdlt.sync.SyncServiceRunner.LocalSyncRequestsRx;
import com.radixdlt.sync.SyncServiceRunner.SyncTimeoutsRx;
import com.radixdlt.utils.TypedMocks;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SyncServiceRunnerTest {

	private SyncServiceRunner<LedgerUpdate> syncServiceRunner;
	private LocalSyncRequestsRx localSyncRequestsRx;
	private SyncTimeoutsRx syncTimeoutsRx;
	private StateSyncNetworkRx stateSyncNetwork;
	private LocalSyncServiceProcessor syncServiceProcessor;
	private RemoteSyncResponseProcessor remoteSyncResponseProcessor;
	private RemoteSyncServiceProcessor remoteSyncServiceProcessor;
	private LedgerUpdateProcessor<LedgerUpdate> ledgerUpdateProcessor;
	private Subject<LedgerUpdate> versionUpdatesSubject;
	private Subject<RemoteSyncRequest> requestsSubject;
	private Subject<RemoteSyncResponse> responsesSubject;

	@Before
	public void setUp() {
		this.localSyncRequestsRx = mock(LocalSyncRequestsRx.class);
		when(localSyncRequestsRx.localSyncRequests()).thenReturn(Observable.never());

		this.syncTimeoutsRx = mock(SyncTimeoutsRx.class);
		when(syncTimeoutsRx.timeouts()).thenReturn(Observable.never());

		this.stateSyncNetwork = mock(StateSyncNetworkRx.class);
		when(stateSyncNetwork.syncRequests()).thenReturn(Observable.never());

		this.responsesSubject = PublishSubject.create();
		when(stateSyncNetwork.syncResponses()).thenReturn(responsesSubject);

		this.requestsSubject = PublishSubject.create();
		when(stateSyncNetwork.syncRequests()).thenReturn(requestsSubject);
		this.versionUpdatesSubject = PublishSubject.create();

		this.syncServiceProcessor = mock(LocalSyncServiceProcessor.class);
		this.remoteSyncResponseProcessor = mock(RemoteSyncResponseProcessor.class);
		this.remoteSyncServiceProcessor = mock(RemoteSyncServiceProcessor.class);
		this.ledgerUpdateProcessor = TypedMocks.rmock(LedgerUpdateProcessor.class);

		syncServiceRunner = new SyncServiceRunner<>(
			localSyncRequestsRx,
			syncTimeoutsRx,
			versionUpdatesSubject,
			stateSyncNetwork,
			syncServiceProcessor,
			remoteSyncServiceProcessor,
			remoteSyncResponseProcessor,
			ledgerUpdateProcessor
		);

		// Clear interrupted status
		Thread.interrupted();
	}

	@After
	public void tearDown() {
		syncServiceRunner.stop();
	}

	@Test
	public void when_sync_request__then_it_is_processed() {
		RemoteSyncRequest syncRequest = mock(RemoteSyncRequest.class);
		syncServiceRunner.start();
		requestsSubject.onNext(syncRequest);
		verify(remoteSyncServiceProcessor, timeout(1000).times(1)).processRemoteSyncRequest(eq(syncRequest));
	}

	@Test
	public void when_sync_response__then_it_is_processed() {
		RemoteSyncResponse response = mock(RemoteSyncResponse.class);
		syncServiceRunner.start();
		responsesSubject.onNext(response);
		verify(remoteSyncResponseProcessor, timeout(1000).times(1)).processSyncResponse(eq(response));
	}
}