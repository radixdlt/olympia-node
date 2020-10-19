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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignature;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.liveness.ExponentialTimeoutPacemaker.PacemakerInfoSender;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.SignedNewViewToLeaderSender.BFTNewViewSender;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VertexStore.BFTUpdateSender;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.consensus.sync.BFTSync.BFTSyncTimeoutScheduler;
import com.radixdlt.consensus.sync.BFTSync.SyncVerticesRequestSender;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.consensus.sync.LocalGetVerticesRequest;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.liveness.PacemakerTimeoutSender;
import com.radixdlt.consensus.sync.SyncLedgerRequestSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.utils.UInt256;
import org.junit.Before;
import org.junit.Test;

public class ConsensusModuleTest {
	@Inject
	private BFTSync bftSync;

	@Inject
	private VertexStore vertexStore;

	@Inject
	private Hasher hasher;

	private BFTConfiguration bftConfiguration;

	private ECKeyPair ecKeyPair;
	private SyncVerticesRequestSender requestSender;

	@Before
	public void setup() {
		this.bftConfiguration = mock(BFTConfiguration.class);
		UnverifiedVertex genesis = UnverifiedVertex.createGenesis(LedgerHeader.genesis(Hash.ZERO_HASH, null));
		VerifiedVertex hashedGenesis = new VerifiedVertex(genesis, Hash.ZERO_HASH);
		QuorumCertificate qc = QuorumCertificate.ofGenesis(hashedGenesis, LedgerHeader.genesis(Hash.ZERO_HASH, null));
		when(bftConfiguration.getGenesisVertex()).thenReturn(hashedGenesis);
		when(bftConfiguration.getGenesisQC()).thenReturn(qc);
		when(bftConfiguration.getGenesisHeader()).thenReturn(mock(VerifiedLedgerHeaderAndProof.class));
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		BFTValidator validator = mock(BFTValidator.class);
		when(validator.getPower()).thenReturn(UInt256.ONE);
		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of(validator));
		when(bftConfiguration.getValidatorSet()).thenReturn(validatorSet);
		this.ecKeyPair = ECKeyPair.generateNew();
		this.requestSender = mock(SyncVerticesRequestSender.class);
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
				bind(SyncVerticesRequestSender.class).toInstance(requestSender);
				bind(SyncVerticesResponseSender.class).toInstance(mock(SyncVerticesResponseSender.class));
				bind(NextCommandGenerator.class).toInstance(mock(NextCommandGenerator.class));
				bind(SystemCounters.class).toInstance(mock(SystemCounters.class));
				bind(TimeSupplier.class).toInstance(mock(TimeSupplier.class));
				bind(PacemakerInfoSender.class).toInstance(mock(PacemakerInfoSender.class));
				bind(PacemakerTimeoutSender.class).toInstance(mock(PacemakerTimeoutSender.class));
				bind(BFTSyncTimeoutScheduler.class).toInstance(mock(BFTSyncTimeoutScheduler.class));
				bind(BFTConfiguration.class).toInstance(bftConfiguration);
				bind(Integer.class).annotatedWith(BFTSyncPatienceMillis.class).toInstance(200);
			}

			@Provides
			@Named("self")
			private BFTNode bftNode() {
				return BFTNode.create(ecKeyPair.getPublicKey());
			}

			@Provides
			@Named("self")
			private ECKeyPair keyPair() {
				return ecKeyPair;
			}
		};
	}

	@Test
	public void on_sync_request_timeout_should_retry() {
		// Arrange
		Guice.createInjector(
			new ConsensusModule(500, 2.0, 32),
			new CryptoModule(),
			getExternalModule()
		).injectMembers(this);
		QuorumCertificate parent = vertexStore.syncInfo().highestQC();
		UnverifiedVertex unverifiedVertex = new UnverifiedVertex(parent, View.of(1), new Command(new byte[] {0}));
		Hash hash = hasher.hash(unverifiedVertex);
		VerifiedVertex verifiedVertex = new VerifiedVertex(unverifiedVertex, hash);
		BFTHeader next = new BFTHeader(
			View.of(1),
			verifiedVertex.getId(),
			LedgerHeader.create(1, View.of(1), new AccumulatorState(1, Hash.ZERO_HASH), 1)
		);
		VoteData voteData = new VoteData(
			next,
			parent.getProposed(),
			parent.getParent()
		);
		BFTNode bftNode = BFTNode.random();
		QuorumCertificate unsyncedQC = new QuorumCertificate(
			voteData,
			new TimestampedECDSASignatures(ImmutableMap.of(bftNode, TimestampedECDSASignature.from(0, UInt256.ONE, new ECDSASignature())))
		);
		HighQC unsyncedHighQC = HighQC.from(unsyncedQC, unsyncedQC);
		bftSync.syncToQC(unsyncedHighQC, bftNode);

		// Act
		bftSync.processGetVerticesLocalTimeout(new LocalGetVerticesRequest(hash, 1));

		// Assert
		verify(requestSender, times(2)).sendGetVerticesRequest(eq(bftNode), eq(new LocalGetVerticesRequest(hash, 1)));
	}
}