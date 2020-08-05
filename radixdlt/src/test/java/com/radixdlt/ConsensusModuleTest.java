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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.radixdlt.consensus.CommittedStateSyncRx;
import com.radixdlt.consensus.ConsensusEventsRx;
import com.radixdlt.consensus.ConsensusRunner;
import com.radixdlt.consensus.EpochChangeRx;
import com.radixdlt.consensus.SyncEpochsRPCRx;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.SyncedStateComputer;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VertexStore.SyncVerticesRPCSender;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.epoch.EpochManager.EpochInfoSender;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.network.TimeSupplier;
import io.reactivex.rxjava3.core.Observable;
import org.junit.Test;

public class ConsensusModuleTest {
	private static class ExternalConsensusModule extends AbstractModule {
		@Override
		protected void configure() {
			CommittedStateSyncRx committedStateSyncRx = mock(CommittedStateSyncRx.class);
			when(committedStateSyncRx.committedStateSyncs()).thenReturn(Observable.never());
			bind(CommittedStateSyncRx.class).toInstance(committedStateSyncRx);

			ConsensusEventsRx consensusEventsRx = mock(ConsensusEventsRx.class);
			when(consensusEventsRx.consensusEvents()).thenReturn(Observable.never());
			bind(ConsensusEventsRx.class).toInstance(consensusEventsRx);

			EpochChangeRx epochChangeRx = mock(EpochChangeRx.class);
			when(epochChangeRx.epochChanges()).thenReturn(Observable.never());
			bind(EpochChangeRx.class).toInstance(epochChangeRx);

			SyncEpochsRPCRx syncEpochsRPCRx = mock(SyncEpochsRPCRx.class);
			when(syncEpochsRPCRx.epochResponses()).thenReturn(Observable.never());
			when(syncEpochsRPCRx.epochRequests()).thenReturn(Observable.never());
			bind(SyncEpochsRPCRx.class).toInstance(syncEpochsRPCRx);

			SyncVerticesRPCRx syncVerticesRPCRx = mock(SyncVerticesRPCRx.class);
			when(syncVerticesRPCRx.errorResponses()).thenReturn(Observable.never());
			when(syncVerticesRPCRx.requests()).thenReturn(Observable.never());
			when(syncVerticesRPCRx.responses()).thenReturn(Observable.never());
			bind(SyncVerticesRPCRx.class).toInstance(syncVerticesRPCRx);

			bind(new TypeLiteral<SyncedStateComputer<CommittedAtom>>() { }).toInstance(mock(SyncedStateComputer.class));
			bind(BFTEventSender.class).toInstance(mock(BFTEventSender.class));
			bind(SyncVerticesRPCSender.class).toInstance(mock(SyncVerticesRPCSender.class));
			bind(VertexStoreEventSender.class).toInstance(mock(VertexStoreEventSender.class));
			bind(EpochInfoSender.class).toInstance(mock(EpochInfoSender.class));
			bind(SyncEpochsRPCSender.class).toInstance(mock(SyncEpochsRPCSender.class));
			bind(NextCommandGenerator.class).toInstance(mock(NextCommandGenerator.class));
			bind(SystemCounters.class).toInstance(mock(SystemCounters.class));
			bind(TimeSupplier.class).toInstance(mock(TimeSupplier.class));
			bind(BFTNode.class).annotatedWith(Names.named("self")).toInstance(mock(BFTNode.class));
			bind(ECKeyPair.class).annotatedWith(Names.named("self")).toInstance(mock(ECKeyPair.class));
		}
	}

	@Test
	public void when_configured_with_correct_interfaces__then_consensus_runner_should_be_created() {
		Injector injector = Guice.createInjector(
			new ConsensusModule(500),
			new ExternalConsensusModule()
		);

		ConsensusRunner runner = injector.getInstance(ConsensusRunner.class);
		assertThat(runner).isNotNull();
	}
}