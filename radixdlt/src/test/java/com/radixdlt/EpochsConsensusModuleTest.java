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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.bft.SignedNewViewToLeaderSender.BFTNewViewSender;
import com.radixdlt.consensus.liveness.ExponentialTimeoutPacemaker.PacemakerInfoSender;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VertexStore.BFTUpdateSender;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochManager.EpochInfoSender;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.sync.BFTSync.BFTSyncTimeoutScheduler;
import com.radixdlt.consensus.sync.SyncLedgerRequestSender;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.sync.BFTSync.SyncVerticesRequestSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.network.TimeSupplier;
import org.junit.Test;

public class EpochsConsensusModuleTest {
	@Inject
	private PacemakerInfoSender infoSender;

	private EpochInfoSender epochInfoSender = mock(EpochInfoSender.class);
	private ProposerElection proposerElection = mock(ProposerElection.class);

	private Module getExternalModule() {
		return new AbstractModule() {
			@Override
			protected void configure() {
				bind(ProposerElection.class).toInstance(proposerElection);
				bind(BFTUpdateSender.class).toInstance(mock(BFTUpdateSender.class));
				bind(Ledger.class).toInstance(mock(Ledger.class));
				bind(SyncLedgerRequestSender.class).toInstance(mock(SyncLedgerRequestSender.class));
				bind(BFTEventSender.class).toInstance(mock(BFTEventSender.class));
				bind(BFTNewViewSender.class).toInstance(mock(BFTNewViewSender.class));
				bind(SyncVerticesRequestSender.class).toInstance(mock(SyncVerticesRequestSender.class));
				bind(SyncVerticesResponseSender.class).toInstance(mock(SyncVerticesResponseSender.class));
				bind(VertexStoreEventSender.class).toInstance(mock(VertexStoreEventSender.class));
				bind(NextCommandGenerator.class).toInstance(mock(NextCommandGenerator.class));
				bind(SystemCounters.class).toInstance(mock(SystemCounters.class));
				bind(TimeSupplier.class).toInstance(mock(TimeSupplier.class));
				bind(Hasher.class).toInstance(mock(Hasher.class));
				bind(HashVerifier.class).toInstance(mock(HashVerifier.class));
				bind(HashSigner.class).toInstance(mock(HashSigner.class));
				bind(BFTNode.class).annotatedWith(Names.named("self")).toInstance(mock(BFTNode.class));
				bind(BFTConfiguration.class).toInstance(mock(BFTConfiguration.class));
				bind(EpochInfoSender.class).toInstance(epochInfoSender);
				bind(BFTFactory.class).toInstance(mock(BFTFactory.class));
				bind(SyncEpochsRPCSender.class).toInstance(mock(SyncEpochsRPCSender.class));
				bind(LocalTimeoutSender.class).toInstance(mock(LocalTimeoutSender.class));
				bind(BFTSyncTimeoutScheduler.class).toInstance(mock(BFTSyncTimeoutScheduler.class));
				bind(VerifiedLedgerHeaderAndProof.class).toInstance(mock(VerifiedLedgerHeaderAndProof.class));
			}
		};
	}

	@Test
	public void when_send_current_view__then_should_use_epoch_info_sender() {
		Guice.createInjector(
			new EpochsConsensusModule(500, 2.0, 0), // constant for now
			getExternalModule()
		).injectMembers(this);

		View view = mock(View.class);
		infoSender.sendCurrentView(view);

		verify(epochInfoSender, times(1)).sendCurrentView(argThat(e -> e.getView().equals(view)));
	}


	@Test
	public void when_send_timeout_processed__then_should_use_epoch_info_sender() {
		Guice.createInjector(
			new EpochsConsensusModule(500, 2.0, 0), // constant for now
			getExternalModule()
		).injectMembers(this);

		View view = mock(View.class);
		BFTNode node = mock(BFTNode.class);
		when(proposerElection.getProposer(any())).thenReturn(node);
		infoSender.sendTimeoutProcessed(view);

		verify(epochInfoSender, times(1))
			.sendTimeoutProcessed(
				argThat(t -> t.getEpochView().getView().equals(view) && t.getLeader().equals(node))
			);
	}
}