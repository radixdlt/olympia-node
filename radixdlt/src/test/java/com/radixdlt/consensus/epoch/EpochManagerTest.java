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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.bft.BFTSyncer.SyncResult;
import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.epoch.EpochManager.EpochInfoSender;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.sync.SyncLedgerRequestSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.counters.SystemCountersImpl;
import com.google.common.hash.HashCode;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class EpochManagerTest {
	private EpochManager epochManager;
	private EpochManager.SyncEpochsRPCSender syncEpochsRPCSender;
	private EpochInfoSender epochInfoSender;
	private SyncLedgerRequestSender syncRequestSender;
	private VertexStore vertexStore;
	private BFTSync vertexStoreSync;
	private BFTFactory bftFactory;
	private Pacemaker pacemaker;
	private SystemCounters systemCounters;
	private ProposerElection proposerElection;
	private BFTNode self;
	private BFTSyncRequestProcessorFactory requestProcessorFactory;
	private VertexStoreFactory vertexStoreFactory;

	@Before
	public void setup() {
		this.syncEpochsRPCSender = mock(EpochManager.SyncEpochsRPCSender.class);

		this.vertexStore = mock(VertexStore.class);
		this.vertexStoreFactory = mock(VertexStoreFactory.class);
		when(vertexStoreFactory.create(any(), any())).thenReturn(this.vertexStore);

		this.vertexStoreSync = mock(BFTSync.class);
		BFTSyncFactory bftSyncFactory = mock(BFTSyncFactory.class);
		when(bftSyncFactory.create(any(), any())).thenReturn(vertexStoreSync);

		this.pacemaker = mock(Pacemaker.class);

		this.bftFactory = mock(BFTFactory.class);

		this.systemCounters = new SystemCountersImpl();

		this.proposerElection = mock(ProposerElection.class);
		this.self = mock(BFTNode.class);
		when(self.getSimpleName()).thenReturn("Test");

		this.epochInfoSender = mock(EpochInfoSender.class);
		this.syncRequestSender = mock(SyncLedgerRequestSender.class);

		EpochChange initial = mock(EpochChange.class);
		when(initial.getProof()).thenReturn(VerifiedLedgerHeaderAndProof.genesis(mock(HashCode.class), null));
		when(initial.getEpoch()).thenReturn(1L);
		BFTConfiguration config = mock(BFTConfiguration.class);
		when(config.getValidatorSet()).thenReturn(BFTValidatorSet.from(ImmutableSet.of()));
		when(initial.getBFTConfiguration()).thenReturn(config);

		this.requestProcessorFactory = mock(BFTSyncRequestProcessorFactory.class);

		PacemakerFactory pacemakerFactory = (timeoutSender, infoSender, proposalGenerator, proposerElection, validators) -> this.pacemaker;

		this.epochManager = new EpochManager(
			this.self,
			initial,
			this.syncEpochsRPCSender,
			mock(LocalTimeoutSender.class),
			syncRequestSender,
			pacemakerFactory,
			vertexStoreFactory,
			bftSyncFactory,
			requestProcessorFactory,
			proposers -> proposerElection,
			this.bftFactory,
			this.systemCounters,
			this.epochInfoSender
		);
		this.epochManager.start();
	}

	@Test
	public void when_next_epoch_does_not_contain_self__then_should_not_emit_any_consensus_events() {
		EpochChange epochChange = mock(EpochChange.class);
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		when(validatorSet.containsNode(eq(self))).thenReturn(false);
		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of());
		BFTConfiguration config = mock(BFTConfiguration.class);
		when(config.getValidatorSet()).thenReturn(validatorSet);
		when(epochChange.getBFTConfiguration()).thenReturn(config);
		when(epochChange.getEpoch()).thenReturn(2L);
		EpochsLedgerUpdate epochsLedgerUpdate = mock(EpochsLedgerUpdate.class);
		when(epochsLedgerUpdate.getEpochChange()).thenReturn(Optional.of(epochChange));
		epochManager.processLedgerUpdate(epochsLedgerUpdate);

		verify(bftFactory, never()).create(any(), any(), any(), any(), any(), any());
		verify(syncEpochsRPCSender, never()).sendGetEpochRequest(any(), anyLong());
	}

	@Test
	public void when_no_epoch_change__then_processing_events_should_not_fail() {
		epochManager.processLocalTimeout(mock(LocalTimeout.class));
		epochManager.processBFTUpdate(mock(BFTUpdate.class));
		epochManager.processGetVerticesRequest(mock(GetVerticesRequest.class));
		epochManager.processGetVerticesResponse(mock(GetVerticesResponse.class));
		epochManager.processLedgerUpdate(mock(EpochsLedgerUpdate.class));
		epochManager.processConsensusEvent(mock(Proposal.class));
		epochManager.processConsensusEvent(mock(Vote.class));
		epochManager.processConsensusEvent(mock(ViewTimeout.class));
	}

	@Test
	public void when_receive_next_epoch_then_epoch_request__then_should_return_current_ancestor() {
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of());
		EpochChange epochChange = mock(EpochChange.class);
		VerifiedLedgerHeaderAndProof proof = mock(VerifiedLedgerHeaderAndProof.class);
		when(epochChange.getProof()).thenReturn(proof);
		BFTConfiguration config = mock(BFTConfiguration.class);
		when(config.getValidatorSet()).thenReturn(validatorSet);
		when(epochChange.getBFTConfiguration()).thenReturn(config);
		when(epochChange.getEpoch()).thenReturn(2L);
		EpochsLedgerUpdate epochsLedgerUpdate = mock(EpochsLedgerUpdate.class);
		when(epochsLedgerUpdate.getEpochChange()).thenReturn(Optional.of(epochChange));
		epochManager.processLedgerUpdate(epochsLedgerUpdate);

		BFTNode sender = mock(BFTNode.class);
		epochManager.processGetEpochRequest(new GetEpochRequest(sender, 1L));

		verify(syncEpochsRPCSender, times(1)).sendGetEpochResponse(eq(sender), eq(proof));
	}

	@Test
	public void when_receive_not_current_epoch_request__then_should_return_null() {
		BFTNode sender = mock(BFTNode.class);
		epochManager.processGetEpochRequest(new GetEpochRequest(sender, 2));
		verify(syncEpochsRPCSender, times(1)).sendGetEpochResponse(eq(sender), isNull());
	}

	@Test
	public void when_receive_epoch_response__then_should_sync_state_computer() {
		GetEpochResponse response = mock(GetEpochResponse.class);
		VerifiedLedgerHeaderAndProof proof = mock(VerifiedLedgerHeaderAndProof.class);
		when(proof.getEpoch()).thenReturn(1L);
		when(response.getEpochProof()).thenReturn(proof);
		when(response.getAuthor()).thenReturn(mock(BFTNode.class));
		epochManager.processGetEpochResponse(response);
		verify(syncRequestSender, times(1))
			.sendLocalSyncRequest(argThat(req -> req.getTarget().equals(proof)));
	}

	@Test
	public void when_receive_null_epoch_response__then_should_do_nothing() {
		GetEpochResponse response = mock(GetEpochResponse.class);
		when(response.getEpochProof()).thenReturn(null);
		epochManager.processGetEpochResponse(response);
		verify(syncRequestSender, never()).sendLocalSyncRequest(any());
	}

	@Test
	public void when_receive_old_epoch_response__then_should_do_nothing() {
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of());
		EpochChange epochChange = mock(EpochChange.class);
		BFTConfiguration config = mock(BFTConfiguration.class);
		when(config.getValidatorSet()).thenReturn(validatorSet);
		when(epochChange.getBFTConfiguration()).thenReturn(config);
		when(epochChange.getEpoch()).thenReturn(2L);
		EpochsLedgerUpdate epochsLedgerUpdate = mock(EpochsLedgerUpdate.class);
		when(epochsLedgerUpdate.getEpochChange()).thenReturn(Optional.of(epochChange));
		epochManager.processLedgerUpdate(epochsLedgerUpdate);

		GetEpochResponse response = mock(GetEpochResponse.class);
		VerifiedLedgerHeaderAndProof proof = mock(VerifiedLedgerHeaderAndProof.class);
		when(proof.getEpoch()).thenReturn(1L);
		when(response.getEpochProof()).thenReturn(proof);
		epochManager.processGetEpochResponse(response);
		verify(syncRequestSender, never()).sendLocalSyncRequest(any());
	}

	// TODO: Refactor EpochManager to simplify the following testing logic (TDD)
	@Test
	public void when_epoch_change_and_then_epoch_events__then_should_execute_events() {
		BFTEventProcessor eventProcessor = mock(BFTEventProcessor.class);
		when(bftFactory.create(any(), any(), any(), any(), any(), any())).thenReturn(eventProcessor);

		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		when(validatorSet.containsNode(any())).thenReturn(true);

		BFTValidator validator = mock(BFTValidator.class);
		BFTNode node = mock(BFTNode.class);
		when(validator.getNode()).then(i -> node);

		BFTValidator selfValidator = mock(BFTValidator.class);
		when(selfValidator.getNode()).thenReturn(this.self);

		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of(selfValidator, validator));
		EpochChange epochChange = mock(EpochChange.class);
		when(epochChange.getEpoch()).thenReturn(2L);
		BFTConfiguration configuration = mock(BFTConfiguration.class);
		when(configuration.getValidatorSet()).thenReturn(validatorSet);
		when(epochChange.getBFTConfiguration()).thenReturn(configuration);
		EpochsLedgerUpdate epochsLedgerUpdate = mock(EpochsLedgerUpdate.class);
		when(epochsLedgerUpdate.getEpochChange()).thenReturn(Optional.of(epochChange));
		epochManager.processLedgerUpdate(epochsLedgerUpdate);

		verify(eventProcessor, times(1)).start();

		when(vertexStoreSync.syncToQC(any(), any())).thenReturn(SyncResult.SYNCED);
		when(pacemaker.getCurrentView()).thenReturn(View.of(0));

		Proposal proposal = mock(Proposal.class);
		when(proposal.getAuthor()).thenReturn(node);
		when(proposal.getEpoch()).thenReturn(2L);
		UnverifiedVertex vertex = mock(UnverifiedVertex.class);
		when(vertex.getView()).thenReturn(View.of(1));
		when(proposal.getVertex()).thenReturn(vertex);
		epochManager.processConsensusEvent(proposal);
		verify(eventProcessor, times(1)).processProposal(eq(proposal));

		when(proposerElection.getProposer(any())).thenReturn(this.self);

		ViewTimeout viewTimeout = mock(ViewTimeout.class);
		when(viewTimeout.getView()).thenReturn(View.of(1));
		when(viewTimeout.getAuthor()).thenReturn(this.self);
		when(viewTimeout.getEpoch()).thenReturn(2L);
		epochManager.processConsensusEvent(viewTimeout);
		verify(eventProcessor, times(1)).processViewTimeout(eq(viewTimeout));


		when(pacemaker.getCurrentView()).thenReturn(View.of(0));

		Vote vote = mock(Vote.class);
		VoteData voteData = mock(VoteData.class);
		BFTHeader proposed = mock(BFTHeader.class);
		when(proposed.getView()).thenReturn(View.of(1));
		when(voteData.getProposed()).thenReturn(proposed);
		when(vote.getVoteData()).thenReturn(voteData);
		when(vote.getAuthor()).thenReturn(node);
		when(vote.getEpoch()).thenReturn(2L);
		epochManager.processConsensusEvent(vote);
		verify(eventProcessor, times(1)).processVote(eq(vote));

		ConsensusEvent unknownEvent = mock(ConsensusEvent.class);
		when(unknownEvent.getAuthor()).thenReturn(mock(BFTNode.class));
		when(unknownEvent.getEpoch()).thenReturn(2L);
		assertThatThrownBy(() -> epochManager.processConsensusEvent(unknownEvent))
			.isInstanceOf(IllegalStateException.class);

		BFTUpdate update = mock(BFTUpdate.class);
		epochManager.processBFTUpdate(update);
		verify(eventProcessor, times(1)).processBFTUpdate(eq(update));

		LocalTimeout localTimeout = mock(LocalTimeout.class);
		when(localTimeout.getEpoch()).thenReturn(2L);
		when(localTimeout.getView()).thenReturn(View.of(1));

		epochManager.processLocalTimeout(localTimeout);
		verify(eventProcessor, times(1)).processLocalTimeout(eq(View.of(1)));

		Proposal oldProposal = mock(Proposal.class);
		when(oldProposal.getEpoch()).thenReturn(1L);
		epochManager.processConsensusEvent(oldProposal);
		verify(eventProcessor, never()).processProposal(eq(oldProposal));

		LocalTimeout oldTimeout = mock(LocalTimeout.class);
		when(oldTimeout.getEpoch()).thenReturn(1L);
		View view = mock(View.class);
		when(oldTimeout.getView()).thenReturn(view);
		epochManager.processLocalTimeout(oldTimeout);
		verify(eventProcessor, never()).processLocalTimeout(eq(view));
	}

	@Test
	public void when_receive_next_epoch_events_and_then_epoch_change_and_part_of_validator_set__then_should_execute_queued_epoch_events() {
		BFTValidator authorValidator = mock(BFTValidator.class);
		BFTNode node = mock(BFTNode.class);
		when(authorValidator.getNode()).thenReturn(node);

		when(pacemaker.getCurrentView()).thenReturn(View.genesis());
		when(vertexStore.highQC()).thenReturn(mock(HighQC.class));
		when(vertexStoreSync.syncToQC(any(), any())).thenReturn(SyncResult.SYNCED);

		Proposal proposal = mock(Proposal.class);
		UnverifiedVertex vertex = mock(UnverifiedVertex.class);
		when(vertex.getView()).thenReturn(View.of(1));
		when(proposal.getEpoch()).thenReturn(2L);
		when(proposal.getVertex()).thenReturn(vertex);
		when(proposal.getAuthor()).thenReturn(node);
		epochManager.processConsensusEvent(proposal);

		assertThat(systemCounters.get(CounterType.EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS)).isEqualTo(1);

		BFTEventProcessor eventProcessor = mock(BFTEventProcessor.class);
		when(bftFactory.create(any(), any(), any(), any(), any(), any())).thenReturn(eventProcessor);

		BFTValidator validator = mock(BFTValidator.class);
		when(validator.getNode()).thenReturn(mock(BFTNode.class));
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		when(validatorSet.containsNode(any())).thenReturn(true);
		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of(validator, authorValidator));

		EpochChange epochChange = mock(EpochChange.class);
		when(epochChange.getEpoch()).thenReturn(2L);
		BFTConfiguration configuration = mock(BFTConfiguration.class);
		when(configuration.getValidatorSet()).thenReturn(validatorSet);
		when(epochChange.getBFTConfiguration()).thenReturn(configuration);
		EpochsLedgerUpdate epochsLedgerUpdate = mock(EpochsLedgerUpdate.class);
		when(epochsLedgerUpdate.getEpochChange()).thenReturn(Optional.of(epochChange));
		epochManager.processLedgerUpdate(epochsLedgerUpdate);

		assertThat(systemCounters.get(CounterType.EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS)).isEqualTo(0);
		verify(eventProcessor, times(1)).processProposal(eq(proposal));
	}

	@Test
	public void when_receive_next_epoch_events_and_then_epoch_change_and_not_part_of_validator_set__then_queued_events_should_be_cleared() {
		Proposal proposal = mock(Proposal.class);
		when(proposal.getEpoch()).thenReturn(2L);
		when(proposal.getAuthor()).thenReturn(mock(BFTNode.class));
		epochManager.processConsensusEvent(proposal);
		assertThat(systemCounters.get(CounterType.EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS)).isEqualTo(1);

		BFTValidator validator = mock(BFTValidator.class);
		when(validator.getNode()).thenReturn(mock(BFTNode.class));
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		when(validatorSet.containsNode(any())).thenReturn(false);
		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of());

		EpochChange epochChange = mock(EpochChange.class);
		BFTConfiguration configuration = mock(BFTConfiguration.class);
		when(configuration.getValidatorSet()).thenReturn(validatorSet);
		when(epochChange.getBFTConfiguration()).thenReturn(configuration);
		when(epochChange.getEpoch()).thenReturn(2L);
		EpochsLedgerUpdate epochsLedgerUpdate = mock(EpochsLedgerUpdate.class);
		when(epochsLedgerUpdate.getEpochChange()).thenReturn(Optional.of(epochChange));
		epochManager.processLedgerUpdate(epochsLedgerUpdate);

		assertThat(systemCounters.get(CounterType.EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS)).isEqualTo(0);
	}

	@Test
	public void when_next_epoch__then_get_vertices_rpc_should_be_forwarded_to_vertex_store() {
		when(vertexStore.highQC()).thenReturn(mock(HighQC.class));

		BFTEventProcessor eventProcessor = mock(BFTEventProcessor.class);
		when(bftFactory.create(any(), any(), any(), any(), any(), any())).thenReturn(eventProcessor);

		VertexStoreBFTSyncRequestProcessor requestProcessor = mock(VertexStoreBFTSyncRequestProcessor.class);
		when(requestProcessorFactory.create(any())).thenReturn(requestProcessor);

		BFTValidator validator = mock(BFTValidator.class);
		when(validator.getNode()).thenReturn(mock(BFTNode.class));
		BFTValidatorSet validatorSet = mock(BFTValidatorSet.class);
		when(validatorSet.containsNode(any())).thenReturn(true);
		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of(validator));

		EpochChange epochChange = mock(EpochChange.class);
		when(epochChange.getEpoch()).thenReturn(2L);
		BFTConfiguration configuration = mock(BFTConfiguration.class);
		when(configuration.getValidatorSet()).thenReturn(validatorSet);
		when(epochChange.getBFTConfiguration()).thenReturn(configuration);
		EpochsLedgerUpdate epochsLedgerUpdate = mock(EpochsLedgerUpdate.class);
		when(epochsLedgerUpdate.getEpochChange()).thenReturn(Optional.of(epochChange));
		epochManager.processLedgerUpdate(epochsLedgerUpdate);

		GetVerticesRequest getVerticesRequest = mock(GetVerticesRequest.class);
		epochManager.processGetVerticesRequest(getVerticesRequest);
		verify(requestProcessor, times(1)).processGetVerticesRequest(eq(getVerticesRequest));

		GetVerticesResponse getVerticesResponse = mock(GetVerticesResponse.class);
		epochManager.processGetVerticesResponse(getVerticesResponse);
		verify(vertexStoreSync, times(1)).processGetVerticesResponse(eq(getVerticesResponse));

		GetVerticesErrorResponse getVerticesErrorResponse = mock(GetVerticesErrorResponse.class);
		epochManager.processGetVerticesErrorResponse(getVerticesErrorResponse);
		verify(vertexStoreSync, times(1)).processGetVerticesErrorResponse(eq(getVerticesErrorResponse));

		EpochsLedgerUpdate ledgerUpdate = mock(EpochsLedgerUpdate.class);
		epochManager.processLedgerUpdate(ledgerUpdate);
		verify(vertexStoreSync, times(1)).processLedgerUpdate(eq(ledgerUpdate));
	}
}
