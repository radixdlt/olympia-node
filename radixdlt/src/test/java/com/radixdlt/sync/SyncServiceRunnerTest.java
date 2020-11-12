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

import com.google.common.collect.ImmutableSet;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.sync.SyncServiceRunner.SyncTimeoutsRx;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SyncServiceRunnerTest {

	private SyncServiceRunner<LedgerUpdate> syncServiceRunner;
	private Subject<LocalSyncRequest> localSyncRequests;
	private SyncTimeoutsRx syncTimeoutsRx;
	private LocalSyncServiceProcessor syncServiceProcessor;
	private RemoteEventProcessor<DtoLedgerHeaderAndProof> remoteSyncServiceProcessor;
	private RemoteEventProcessor<DtoCommandsAndProof> responseProcessor;
	private EventProcessor<LocalSyncRequest> localSyncRequestEventProcessor;
	private Set<EventProcessor<LedgerUpdate>> ledgerUpdateProcessors;
	private Subject<LedgerUpdate> versionUpdatesSubject;

	private Subject<RemoteEvent<DtoLedgerHeaderAndProof>> remoteSyncRequests;
	private Subject<RemoteEvent<DtoCommandsAndProof>> remoteSyncResponses;

	@Before
	public void setUp() {
		this.syncTimeoutsRx = mock(SyncTimeoutsRx.class);
		when(syncTimeoutsRx.timeouts()).thenReturn(Observable.never());

		this.localSyncRequests = PublishSubject.create();
		this.remoteSyncRequests = PublishSubject.create();
		this.remoteSyncResponses = PublishSubject.create();
		this.versionUpdatesSubject = PublishSubject.create();

		this.syncServiceProcessor = mock(LocalSyncServiceProcessor.class);
		this.remoteSyncServiceProcessor = rmock(RemoteEventProcessor.class);
		this.responseProcessor = rmock(RemoteEventProcessor.class);
		this.ledgerUpdateProcessors = ImmutableSet.of();

		this.localSyncRequestEventProcessor = rmock(EventProcessor.class);

		syncServiceRunner = new SyncServiceRunner<>(
			localSyncRequests,
			localSyncRequestEventProcessor,
			syncTimeoutsRx,
			syncServiceProcessor,
			versionUpdatesSubject,
			ledgerUpdateProcessors,
			remoteSyncRequests,
			remoteSyncServiceProcessor,
			remoteSyncResponses,
			responseProcessor
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
		RemoteEvent<DtoLedgerHeaderAndProof> syncRequest = rmock(RemoteEvent.class);
		syncServiceRunner.start();
		remoteSyncRequests.onNext(syncRequest);
		verify(remoteSyncServiceProcessor, timeout(1000).times(1)).process(any(), any());
	}

	@Test
	public void when_sync_response__then_it_is_processed() {
		RemoteEvent<DtoCommandsAndProof> response = rmock(RemoteEvent.class);
		syncServiceRunner.start();
		remoteSyncResponses.onNext(response);
		verify(responseProcessor, timeout(1000).times(1)).process(any(), any());
	}
}