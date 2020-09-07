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

package com.radixdlt.integration.distributed.simulation;

import com.google.inject.Scopes;
import com.radixdlt.ModuleRunner;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.ProposerElectionFactory;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Timeout;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.VertexStoreEventProcessor;
import com.radixdlt.consensus.VertexStoreFactory;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTInfoSender;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochManager.EpochInfoSender;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.epoch.LocalTimeout;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.VerifiedCommandsAndProof;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.sync.SyncRequestSender;
import com.radixdlt.consensus.LedgerHeader;

public class MockedLedgerModule extends AbstractModule {
	@Override
	public void configure() {
		bind(NextCommandGenerator.class).toInstance((view, aids) -> null);
		bind(SyncRequestSender.class).toInstance(req -> { });
		bind(ModuleRunner.class).to(BFTRunner.class).in(Scopes.SINGLETON);
		bind(VertexStoreEventProcessor.class).to(VertexStore.class).in(Scopes.SINGLETON);
	}

	@Provides
	@Singleton
	public VertexStore vertexStore(
		Vertex genesisVertex,
		QuorumCertificate genesisQC,
		VertexStoreFactory vertexStoreFactory,
		Ledger ledger
	) {
		return vertexStoreFactory.create(genesisVertex, genesisQC, ledger);
	}

	@Provides
	@Singleton
	public BFTInfoSender bftInfoSender(EpochInfoSender epochInfoSender) {
		return new BFTInfoSender() {
			@Override
			public void sendCurrentView(View view) {
				epochInfoSender.sendCurrentView(EpochView.of(1, view));
			}

			@Override
			public void sendTimeoutProcessed(View view, BFTNode leader) {
				epochInfoSender.sendTimeoutProcessed(new Timeout(EpochView.of(1, view), leader));
			}
		};
	}

	@Provides
	@Singleton
	public BFTEventProcessor eventProcessor(
		BFTFactory bftFactory,
		PacemakerFactory pacemakerFactory,
		VertexStore vertexStore,
		ProposerElectionFactory proposerElectionFactory,
		BFTValidatorSet validatorSet,
		LocalTimeoutSender localTimeoutSender,
		BFTInfoSender infoSender
	) {
		return bftFactory.create(
			header -> { },
			pacemakerFactory.create((view, ms) -> localTimeoutSender.scheduleTimeout(new LocalTimeout(1, view), ms)),
			vertexStore,
			proposerElectionFactory.create(validatorSet),
			validatorSet,
			infoSender
		);
	}

	@Provides
	@Singleton
	Ledger syncedLedger() {
		return new Ledger() {
			@Override
			public LedgerHeader prepare(Vertex vertex) {
				return LedgerHeader.create(
					vertex.getQC().getProposed().getLedgerState().getEpoch(),
					vertex.getView(),
					0,
					Hash.ZERO_HASH,
					0L,
					false
				);
			}

			@Override
			public OnSynced ifCommitSynced(VerifiedLedgerHeaderAndProof header) {
				return onSynced -> {
					onSynced.run();
					return (notSynced, opaque) -> { };
				};
			}

			@Override
			public void commit(VerifiedCommandsAndProof command) {
				// Nothing to do here
			}
		};
	}
}
