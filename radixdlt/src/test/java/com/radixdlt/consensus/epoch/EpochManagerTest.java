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

package com.radixdlt.consensus.epoch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.radixdlt.ConsensusModule;
import com.radixdlt.CryptoModule;
import com.radixdlt.EpochsConsensusModule;
import com.radixdlt.LedgerModule;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VertexStore.BFTUpdateSender;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.liveness.ProceedToViewSender;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;
import com.radixdlt.consensus.sync.BFTSync.BFTSyncTimeoutScheduler;
import com.radixdlt.consensus.sync.BFTSync.SyncVerticesRequestSender;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.epoch.EpochManager.EpochInfoSender;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.sync.SyncLedgerRequestSender;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.StateComputerLedger.LedgerUpdateSender;
import com.radixdlt.ledger.StateComputerLedger.PreparedCommand;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.store.LastProof;
import com.radixdlt.utils.UInt256;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;

public class EpochManagerTest {
	@Inject
	private EpochManager epochManager;

	@Inject
	private Hasher hasher;

	@Inject
	private BFTValidatorSet validatorSet;

	private ECKeyPair ecKeyPair = ECKeyPair.generateNew();

	private BFTUpdateSender bftUpdateSender = mock(BFTUpdateSender.class);
	private EpochInfoSender epochInfoSender = mock(EpochInfoSender.class);
	private SyncEpochsRPCSender syncEpochsRPCSender = mock(SyncEpochsRPCSender.class);
	private LocalTimeoutSender localTimeoutSender = mock(LocalTimeoutSender.class);
	private NextCommandGenerator nextCommandGenerator = mock(NextCommandGenerator.class);
	private ProceedToViewSender proceedToViewSender = mock(ProceedToViewSender.class);
	private ProposalBroadcaster proposalBroadcaster = mock(ProposalBroadcaster.class);
	private BFTSyncTimeoutScheduler timeoutScheduler = mock(BFTSyncTimeoutScheduler.class);
	private SyncVerticesRequestSender syncVerticesRequestSender = mock(SyncVerticesRequestSender.class);
	private SyncLedgerRequestSender syncLedgerRequestSender = mock(SyncLedgerRequestSender.class);
	private SyncVerticesResponseSender syncVerticesResponseSender = mock(SyncVerticesResponseSender.class);
	private LedgerUpdateSender ledgerUpdateSender = mock(LedgerUpdateSender.class);
	private Mempool mempool = mock(Mempool.class);
	private StateComputer stateComputer = new StateComputer() {
		@Override
		public StateComputerResult prepare(ImmutableList<PreparedCommand> previous, Command next, View view, long timestamp) {
			return new StateComputerResult(ImmutableList.of(), ImmutableMap.of());
		}

		@Override
		public void commit(VerifiedCommandsAndProof verifiedCommandsAndProof) {
		}
	};

	private Module getExternalModule() {
		BFTNode self = BFTNode.create(ecKeyPair.getPublicKey());
		return new AbstractModule() {
			@Override
			protected void configure() {
				bind(HashSigner.class).toInstance(ecKeyPair::sign);
				bind(BFTNode.class).annotatedWith(Self.class).toInstance(self);
				bind(BFTUpdateSender.class).toInstance(bftUpdateSender);
				bind(EpochInfoSender.class).toInstance(epochInfoSender);
				bind(SyncEpochsRPCSender.class).toInstance(syncEpochsRPCSender);
				bind(LocalTimeoutSender.class).toInstance(localTimeoutSender);
				bind(NextCommandGenerator.class).toInstance(nextCommandGenerator);
				bind(ProceedToViewSender.class).toInstance(proceedToViewSender);
				bind(ProposalBroadcaster.class).toInstance(proposalBroadcaster);
				bind(BFTSyncTimeoutScheduler.class).toInstance(timeoutScheduler);
				bind(SystemCounters.class).toInstance(new SystemCountersImpl());
				bind(SyncVerticesRequestSender.class).toInstance(syncVerticesRequestSender);
				bind(SyncVerticesResponseSender.class).toInstance(syncVerticesResponseSender);
				bind(SyncLedgerRequestSender.class).toInstance(syncLedgerRequestSender);
				bind(LedgerUpdateSender.class).toInstance(ledgerUpdateSender);
				bind(Mempool.class).toInstance(mempool);
				bind(StateComputer.class).toInstance(stateComputer);
				bindConstant().annotatedWith(BFTSyncPatienceMillis.class).to(50);
				bindConstant().annotatedWith(PacemakerTimeout.class).to(10L);
				bindConstant().annotatedWith(PacemakerRate.class).to(2.0);
				bindConstant().annotatedWith(PacemakerMaxExponent.class).to(0);
				bind(TimeSupplier.class).toInstance(System::currentTimeMillis);
			}

			@Provides
			BFTValidatorSet validatorSet() {
				return BFTValidatorSet.from(Stream.of(BFTValidator.from(self, UInt256.ONE)));
			}

			@Provides
			@LastProof
			VerifiedLedgerHeaderAndProof verifiedLedgerHeaderAndProof(BFTValidatorSet validatorSet) {
				return VerifiedLedgerHeaderAndProof.genesis(HashUtils.zero256(), validatorSet);
			}

			@Provides
			@LastEpochProof
			VerifiedLedgerHeaderAndProof lastEpochProof(BFTValidatorSet validatorSet) {
				return VerifiedLedgerHeaderAndProof.genesis(HashUtils.zero256(), validatorSet);
			}

			@Provides
			BFTConfiguration bftConfiguration(@Self BFTNode self, Hasher hasher, BFTValidatorSet validatorSet) {
				UnverifiedVertex unverifiedVertex = UnverifiedVertex.createGenesis(
					LedgerHeader.genesis(HashUtils.zero256(), validatorSet)
				);
				VerifiedVertex verifiedVertex = new VerifiedVertex(unverifiedVertex, hasher.hash(unverifiedVertex));
				return new BFTConfiguration(
					validatorSet,
					verifiedVertex,
					QuorumCertificate.ofGenesis(verifiedVertex, LedgerHeader.genesis(HashUtils.zero256(), validatorSet))
				);
			}
		};
	}

	@Before
	public void setup() {
		Guice.createInjector(
			new CryptoModule(),
			new ConsensusModule(),
			new EpochsConsensusModule(),
			new LedgerModule(),
			getExternalModule()
		).injectMembers(this);
	}

	@Test
	public void should_not_send_consensus_messages_if_not_part_of_new_epoch() {
		// Arrange
		epochManager.start();
		BFTValidatorSet nextValidatorSet = BFTValidatorSet.from(Stream.of(BFTValidator.from(BFTNode.random(), UInt256.ONE)));
		LedgerHeader header = LedgerHeader.genesis(HashUtils.zero256(), nextValidatorSet);
		UnverifiedVertex genesisVertex = UnverifiedVertex.createGenesis(header);
		VerifiedVertex verifiedGenesisVertex = new VerifiedVertex(genesisVertex, hasher.hash(genesisVertex));
		LedgerHeader nextLedgerHeader = LedgerHeader.create(
			header.getEpoch() + 1,
			View.genesis(),
			header.getAccumulatorState(),
			header.timestamp()
		);
		QuorumCertificate genesisQC = QuorumCertificate.ofGenesis(verifiedGenesisVertex, nextLedgerHeader);
		BFTConfiguration bftConfiguration = new BFTConfiguration(nextValidatorSet, verifiedGenesisVertex, genesisQC);
		VerifiedLedgerHeaderAndProof proof = mock(VerifiedLedgerHeaderAndProof.class);
		when(proof.getEpoch()).thenReturn(header.getEpoch() + 1);
		EpochChange epochChange = new EpochChange(proof, bftConfiguration);
		EpochsLedgerUpdate epochsLedgerUpdate = new EpochsLedgerUpdate(mock(LedgerUpdate.class), epochChange);

		// Act
		epochManager.processLedgerUpdate(epochsLedgerUpdate);

		// Assert
		verify(proposalBroadcaster, times(1)).broadcastProposal(any(), any());
		verify(proceedToViewSender, never()).sendVote(any(), any());
	}

	@Test
	public void when_receive_not_current_epoch_request__then_should_return_null() {
		// Arrange

		// Act
		epochManager.processGetEpochRequest(new GetEpochRequest(BFTNode.random(), 2));

		// Assert
		verify(syncEpochsRPCSender, times(1)).sendGetEpochResponse(any(), isNull());
	}

	@Test
	public void when_receive_epoch_response__then_should_sync_state_computer() {
		// Arrange
		VerifiedLedgerHeaderAndProof proof = mock(VerifiedLedgerHeaderAndProof.class);
		when(proof.getEpoch()).thenReturn(1L);
		GetEpochResponse response = new GetEpochResponse(BFTNode.random(), proof);

		// Act
		epochManager.processGetEpochResponse(response);

		// Assert
		verify(syncLedgerRequestSender, times(1))
			.sendLocalSyncRequest(argThat(req -> req.getTarget().equals(proof)));
	}

	@Test
	public void when_receive_null_epoch_response__then_should_do_nothing() {
		// Arrange
		GetEpochResponse response = new GetEpochResponse(BFTNode.random(), null);

		// Act
		epochManager.processGetEpochResponse(response);

		// Assert
		verify(syncLedgerRequestSender, never()).sendLocalSyncRequest(any());
	}
}
