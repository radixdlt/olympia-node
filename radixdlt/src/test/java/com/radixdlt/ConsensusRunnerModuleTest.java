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

package com.radixdlt;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.radixdlt.consensus.BFTEventsRx;
import com.radixdlt.consensus.EpochManagerRunner;
import com.radixdlt.consensus.SyncEpochsRPCRx;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.liveness.PacemakerRx;
import com.radixdlt.consensus.sync.LocalGetVerticesRequest;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.junit.Test;

public class ConsensusRunnerModuleTest {
	private Module getExternalConsensusRunnerModule() {
		return new AbstractModule() {
			@Override
			protected void configure() {
				BFTEventsRx bftEventsRx = mock(BFTEventsRx.class);
				when(bftEventsRx.bftEvents()).thenReturn(Observable.never());
				bind(BFTEventsRx.class).toInstance(bftEventsRx);

				bind(Key.get(new TypeLiteral<Observable<EpochsLedgerUpdate>>() { })).toInstance(PublishSubject.create());
				bind(Key.get(new TypeLiteral<Observable<BFTUpdate>>() { })).toInstance(PublishSubject.create());

				SyncEpochsRPCRx syncEpochsRPCRx = mock(SyncEpochsRPCRx.class);
				when(syncEpochsRPCRx.epochResponses()).thenReturn(Observable.never());
				when(syncEpochsRPCRx.epochRequests()).thenReturn(Observable.never());
				bind(SyncEpochsRPCRx.class).toInstance(syncEpochsRPCRx);

				SyncVerticesRPCRx syncVerticesRPCRx = mock(SyncVerticesRPCRx.class);
				when(syncVerticesRPCRx.errorResponses()).thenReturn(Observable.never());
				when(syncVerticesRPCRx.requests()).thenReturn(Observable.never());
				when(syncVerticesRPCRx.responses()).thenReturn(Observable.never());
				bind(SyncVerticesRPCRx.class).toInstance(syncVerticesRPCRx);

				PacemakerRx pacemakerRx = mock(PacemakerRx.class);
				when(pacemakerRx.localTimeouts()).thenReturn(Observable.never());
				bind(PacemakerRx.class).toInstance(pacemakerRx);

				bind(EpochManager.class).toInstance(mock(EpochManager.class));

				bind(new TypeLiteral<EventProcessor<LocalGetVerticesRequest>>() { }).toInstance(rmock(EventProcessor.class));
				bind(new TypeLiteral<ScheduledEventDispatcher<LocalGetVerticesRequest>>() { }).toInstance(rmock(ScheduledEventDispatcher.class));
				bind(new TypeLiteral<Observable<LocalGetVerticesRequest>>() { }).toInstance(PublishSubject.create());
			}
		};
	}

	@Test
	public void when_configured_with_correct_interfaces__then_consensus_runner_should_be_created() {
		Injector injector = Guice.createInjector(
			new ConsensusRunnerModule(),
			getExternalConsensusRunnerModule()
		);

		EpochManagerRunner runner = injector.getInstance(EpochManagerRunner.class);
		assertThat(runner).isNotNull();
	}
}