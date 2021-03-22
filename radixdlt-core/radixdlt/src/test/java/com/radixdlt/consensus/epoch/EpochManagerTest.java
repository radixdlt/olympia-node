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

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.radixdlt.ConsensusModule;
import com.radixdlt.CryptoModule;
import com.radixdlt.EpochsConsensusModule;
import com.radixdlt.LedgerModule;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.bft.BFTRebuildUpdate;
import com.radixdlt.consensus.bft.NoVote;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.ViewQuorumReached;
import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.bft.PersistentVertexStore;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.liveness.LocalTimeoutOccurrence;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.StateComputerLedger.PreparedCommand;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.network.GetVerticesRequestRateLimit;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.store.LastProof;
import com.radixdlt.sync.messages.local.LocalSyncRequest;
import com.radixdlt.sync.messages.remote.LedgerStatusUpdate;
import com.radixdlt.utils.UInt256;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;

public class EpochManagerTest {
	@Inject
	private EpochManager epochManager;

	@Inject
	private Hasher hasher;

	private ECKeyPair ecKeyPair = ECKeyPair.generateNew();

	private NextCommandGenerator nextCommandGenerator = mock(NextCommandGenerator.class);
	private ProposalBroadcaster proposalBroadcaster = mock(ProposalBroadcaster.class);
	private ScheduledEventDispatcher<GetVerticesRequest> timeoutScheduler = rmock(ScheduledEventDispatcher.class);
	private EventDispatcher<LocalSyncRequest> syncLedgerRequestSender = rmock(EventDispatcher.class);
	private SyncVerticesResponseSender syncVerticesResponseSender = mock(SyncVerticesResponseSender.class);
	private RemoteEventDispatcher<Vote> voteDispatcher = rmock(RemoteEventDispatcher.class);
	private Mempool mempool = mock(Mempool.class);
	private StateComputer stateComputer = new StateComputer() {
		@Override
		public void addToMempool(Command command, BFTNode origin) {
			// No-op
		}

		@Override
		public Command getNextCommandFromMempool(ImmutableList<PreparedCommand> prepared) {
			return null;
		}

		@Override
		public StateComputerResult prepare(ImmutableList<PreparedCommand> previous, Command next, long epoch, View view, long timestamp) {
			return new StateComputerResult(ImmutableList.of(), ImmutableMap.of());
		}

		@Override
		public void commit(VerifiedCommandsAndProof verifiedCommandsAndProof, VerifiedVertexStoreState vertexStoreState) {
			// No-op
		}
	};

	private Module getExternalModule() {
		BFTNode self = BFTNode.create(ecKeyPair.getPublicKey());
		return new AbstractModule() {
			@Override
			protected void configure() {
				bind(HashSigner.class).toInstance(ecKeyPair::sign);
				bind(BFTNode.class).annotatedWith(Self.class).toInstance(self);
				bind(new TypeLiteral<EventDispatcher<LocalTimeoutOccurrence>>() { }).toInstance(rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<BFTInsertUpdate>>() { }).toInstance(rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<BFTRebuildUpdate>>() { }).toInstance(rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<BFTHighQCUpdate>>() { }).toInstance(rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<BFTCommittedUpdate>>() { }).toInstance(rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<EpochLocalTimeoutOccurrence>>() { }).toInstance(rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<EpochView>>() { }).toInstance(rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<LocalSyncRequest>>() { }).toInstance(syncLedgerRequestSender);
				bind(new TypeLiteral<EventDispatcher<ViewQuorumReached>>() { }).toInstance(rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<EpochViewUpdate>>() { }).toInstance(rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<ViewUpdate>>() { }).toInstance(rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<NoVote>>() { }).toInstance(rmock(EventDispatcher.class));
				bind(new TypeLiteral<EventDispatcher<LedgerUpdate>>() { }).toInstance(rmock(EventDispatcher.class));
				bind(new TypeLiteral<ScheduledEventDispatcher<GetVerticesRequest>>() { }).toInstance(timeoutScheduler);
				bind(new TypeLiteral<ScheduledEventDispatcher<ScheduledLocalTimeout>>() { })
					.toInstance(rmock(ScheduledEventDispatcher.class));
				bind(new TypeLiteral<ScheduledEventDispatcher<Epoched<ScheduledLocalTimeout>>>() { })
						.toInstance(rmock(ScheduledEventDispatcher.class));
				bind(new TypeLiteral<ScheduledEventDispatcher<VertexRequestTimeout>>() { })
					.toInstance(rmock(ScheduledEventDispatcher.class));
				bind(new TypeLiteral<RemoteEventDispatcher<Vote>>() { }).toInstance(voteDispatcher);
				bind(new TypeLiteral<RemoteEventDispatcher<GetVerticesRequest>>() { })
					.toInstance(rmock(RemoteEventDispatcher.class));
				bind(new TypeLiteral<RemoteEventDispatcher<LedgerStatusUpdate>>() { })
					.toInstance(rmock(RemoteEventDispatcher.class));

				bind(PersistentSafetyStateStore.class).toInstance(mock(PersistentSafetyStateStore.class));
				bind(NextCommandGenerator.class).toInstance(nextCommandGenerator);
				bind(ProposalBroadcaster.class).toInstance(proposalBroadcaster);
				bind(SystemCounters.class).toInstance(new SystemCountersImpl());
				bind(SyncVerticesResponseSender.class).toInstance(syncVerticesResponseSender);
				bind(Mempool.class).toInstance(mempool);
				bind(StateComputer.class).toInstance(stateComputer);
				bind(PersistentVertexStore.class).toInstance(mock(PersistentVertexStore.class));
				bind(RateLimiter.class).annotatedWith(GetVerticesRequestRateLimit.class)
					.toInstance(RateLimiter.create(Double.MAX_VALUE));
				bindConstant().annotatedWith(BFTSyncPatienceMillis.class).to(50);
				bindConstant().annotatedWith(PacemakerTimeout.class).to(10L);
				bindConstant().annotatedWith(PacemakerRate.class).to(2.0);
				bindConstant().annotatedWith(PacemakerMaxExponent.class).to(0);
				bind(TimeSupplier.class).toInstance(System::currentTimeMillis);

				bind(new TypeLiteral<Consumer<EpochViewUpdate>>() { }).toInstance(rmock(Consumer.class));
			}

			@Provides
			private ViewUpdate view(
				BFTConfiguration bftConfiguration
			) {
				HighQC highQC = bftConfiguration.getVertexStoreState().getHighQC();
				View view = highQC.highestQC().getView().next();
				return ViewUpdate.create(view, highQC, self, self);
			}

			@Provides
			BFTValidatorSet validatorSet() {
				return BFTValidatorSet.from(Stream.of(BFTValidator.from(self, UInt256.ONE)));
			}

			@Provides
			@LastProof
			LedgerProof verifiedLedgerHeaderAndProof(BFTValidatorSet validatorSet) {
				return LedgerProof.genesis(HashUtils.zero256(), validatorSet);
			}

			@Provides
			@LastEpochProof
			LedgerProof lastEpochProof(BFTValidatorSet validatorSet) {
				return LedgerProof.genesis(HashUtils.zero256(), validatorSet);
			}

			@Provides
			BFTConfiguration bftConfiguration(@Self BFTNode self, Hasher hasher, BFTValidatorSet validatorSet) {
				UnverifiedVertex unverifiedVertex = UnverifiedVertex.createGenesis(
					LedgerHeader.genesis(HashUtils.zero256(), validatorSet)
				);
				VerifiedVertex verifiedVertex = new VerifiedVertex(unverifiedVertex, hasher.hash(unverifiedVertex));

				var qc = QuorumCertificate.ofGenesis(verifiedVertex, LedgerHeader.genesis(HashUtils.zero256(), validatorSet));
				return new BFTConfiguration(
					validatorSet,
					VerifiedVertexStoreState.create(HighQC.from(qc), verifiedVertex, Optional.empty())
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
		BFTConfiguration bftConfiguration = new BFTConfiguration(
			nextValidatorSet,
			VerifiedVertexStoreState.create(HighQC.from(genesisQC), verifiedGenesisVertex, Optional.empty())
		);
		LedgerProof proof = mock(LedgerProof.class);
		when(proof.getEpoch()).thenReturn(header.getEpoch() + 1);
		EpochChange epochChange = new EpochChange(proof, bftConfiguration);
		EpochsLedgerUpdate epochsLedgerUpdate = new EpochsLedgerUpdate(mock(LedgerUpdate.class), epochChange);

		// Act
		epochManager.epochsLedgerUpdateEventProcessor().process(epochsLedgerUpdate);

		// Assert
		verify(proposalBroadcaster, never()).broadcastProposal(argThat(p -> p.getEpoch() == epochChange.getEpoch()), any());
		verify(voteDispatcher, never()).dispatch(any(BFTNode.class), any());
	}
}
