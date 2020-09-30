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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.liveness.ExponentialTimeoutPacemaker.PacemakerInfoSender;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.SignedNewViewToLeaderSender.BFTNewViewSender;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VertexStore.BFTUpdateSender;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.sync.VertexStoreSync.SyncVerticesRequestSender;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.liveness.PacemakerTimeoutSender;
import com.radixdlt.consensus.sync.SyncLedgerRequestSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.Hash;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.utils.UInt256;
import org.junit.Before;
import org.junit.Test;

public class ConsensusModuleTest {
	@Inject
	private BFTEventProcessor eventProcessor;

	private BFTConfiguration bftConfiguration;

	@Before
	public void setup() {
		this.bftConfiguration = mock(BFTConfiguration.class);
		UnverifiedVertex genesis = UnverifiedVertex.createGenesis(LedgerHeader.genesis(Hash.ZERO_HASH));
		VerifiedVertex hashedGenesis = new VerifiedVertex(genesis, Hash.ZERO_HASH);
		QuorumCertificate qc = QuorumCertificate.ofGenesis(hashedGenesis, LedgerHeader.genesis(Hash.ZERO_HASH));
		when(bftConfiguration.getGenesisVertex()).thenReturn(hashedGenesis);
		when(bftConfiguration.getGenesisQC()).thenReturn(qc);
		when(bftConfiguration.getGenesisHeader()).thenReturn(mock(VerifiedLedgerHeaderAndProof.class));
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		BFTValidator validator = mock(BFTValidator.class);
		when(validator.getPower()).thenReturn(UInt256.ONE);
		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of(validator));
		when(bftConfiguration.getValidatorSet()).thenReturn(validatorSet);
	}

	private Module getExternalModule() {
		return new AbstractModule() {
			@Override
			protected void configure() {
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
				bind(PacemakerInfoSender.class).toInstance(mock(PacemakerInfoSender.class));
				bind(PacemakerTimeoutSender.class).toInstance(mock(PacemakerTimeoutSender.class));
				bind(BFTConfiguration.class).toInstance(bftConfiguration);
			}
		};
	}

	@Test
	public void when_configured_with_correct_interfaces__then_consensus_runner_should_be_created() {
		Guice.createInjector(
			new ConsensusModule(500, 2.0, 32),
			getExternalModule()
		).injectMembers(this);

		assertThat(eventProcessor).isNotNull();
	}
}