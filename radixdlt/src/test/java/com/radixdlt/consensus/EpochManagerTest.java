/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.bft.GetVerticesResponse;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.liveness.ScheduledTimeoutSender;
import com.radixdlt.consensus.sync.SyncedRadixEngine;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import org.junit.Before;
import org.junit.Test;

public class EpochManagerTest {
	private ECPublicKey publicKey;
	private EpochManager epochManager;
	private SyncEpochsRPCSender syncEpochsRPCSender;
	private VertexStore vertexStore;
	private BFTFactory bftFactory;
	private Pacemaker pacemaker;
	private SystemCounters systemCounters;

	@Before
	public void setup() {
		ECKeyPair keyPair = ECKeyPair.generateNew();
		this.publicKey = keyPair.getPublicKey();

		this.syncEpochsRPCSender = mock(SyncEpochsRPCSender.class);

		this.vertexStore = mock(VertexStore.class);
		VertexStoreFactory vertexStoreFactory = mock(VertexStoreFactory.class);
		when(vertexStoreFactory.create(any(), any(), any())).thenReturn(this.vertexStore);
		this.pacemaker = mock(Pacemaker.class);

		this.bftFactory = mock(BFTFactory.class);

		this.systemCounters = new SystemCountersImpl();

		this.epochManager = new EpochManager(
			mock(SyncedRadixEngine.class),
			syncEpochsRPCSender,
			mock(ScheduledTimeoutSender.class),
			timeoutSender -> this.pacemaker,
			vertexStoreFactory,
			proposers -> mock(ProposerElection.class),
			bftFactory,
			this.publicKey,
			systemCounters
		);
	}

	@Test
	public void when_next_epoch_does_not_contain_self__then_should_not_emit_any_consensus_events() {
		EpochChange epochChange = mock(EpochChange.class);
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		when(validatorSet.containsKey(eq(publicKey))).thenReturn(false);
		when(epochChange.getValidatorSet()).thenReturn(validatorSet);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(epochChange.getAncestor()).thenReturn(vertexMetadata);
		epochManager.processEpochChange(epochChange);

		verify(bftFactory, never()).create(any(), any(), any(), any());
		verify(syncEpochsRPCSender, never()).sendGetEpochRequest(any(), anyLong());
	}

	@Test
	public void when_no_epoch_change__then_processing_events_should_not_fail() {
		epochManager.processLocalTimeout(mock(LocalTimeout.class));
		epochManager.processLocalSync(mock(Hash.class));
		epochManager.processGetVerticesRequest(mock(GetVerticesRequest.class));
		epochManager.processGetVerticesResponse(mock(GetVerticesResponse.class));
		epochManager.processCommittedStateSync(mock(CommittedStateSync.class));
		epochManager.processConsensusEvent(mock(NewView.class));
		epochManager.processConsensusEvent(mock(Proposal.class));
		epochManager.processConsensusEvent(mock(Vote.class));
	}

	@Test
	public void when_receive_next_epoch_events_and_then_epoch_change_and_part_of_validator_set__then_should_execute_queued_epoch_events() {
		Validator authorValidator = mock(Validator.class);
		ECPublicKey author = mock(ECPublicKey.class);
		when(authorValidator.nodeKey()).thenReturn(author);

		when(pacemaker.getCurrentView()).thenReturn(View.genesis());
		when(vertexStore.getHighestQC()).thenReturn(mock(QuorumCertificate.class));
		when(vertexStore.syncToQC(any(), any(), any())).thenReturn(true);

		VertexMetadata ancestor = VertexMetadata.ofGenesisAncestor();

		Proposal proposal = mock(Proposal.class);
		Vertex vertex = mock(Vertex.class);
		when(vertex.getView()).thenReturn(View.of(1));
		when(proposal.getEpoch()).thenReturn(ancestor.getEpoch() + 1);
		when(proposal.getVertex()).thenReturn(vertex);
		when(proposal.getAuthor()).thenReturn(author);
		epochManager.processConsensusEvent(proposal);

		assertThat(systemCounters.get(CounterType.EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS)).isEqualTo(1);

		BFTEventProcessor eventProcessor = mock(BFTEventProcessor.class);
		when(bftFactory.create(any(), any(), any(), any())).thenReturn(eventProcessor);

		Validator validator = mock(Validator.class);
		when(validator.nodeKey()).thenReturn(mock(ECPublicKey.class));
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		when(validatorSet.containsKey(any())).thenReturn(true);
		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of(validator, authorValidator));
		epochManager.processEpochChange(new EpochChange(ancestor, validatorSet));

		assertThat(systemCounters.get(CounterType.EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS)).isEqualTo(0);
		verify(eventProcessor, times(1)).processProposal(eq(proposal));
	}

	@Test
	public void when_receive_next_epoch_events_and_then_epoch_change_and_not_part_of_validator_set__then_queued_events_should_be_cleared() {
		Validator authorValidator = mock(Validator.class);
		ECPublicKey author = mock(ECPublicKey.class);
		when(authorValidator.nodeKey()).thenReturn(author);

		VertexMetadata ancestor = VertexMetadata.ofGenesisAncestor();

		Proposal proposal = mock(Proposal.class);
		when(proposal.getEpoch()).thenReturn(ancestor.getEpoch() + 1);
		epochManager.processConsensusEvent(proposal);
		assertThat(systemCounters.get(CounterType.EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS)).isEqualTo(1);

		Validator validator = mock(Validator.class);
		when(validator.nodeKey()).thenReturn(mock(ECPublicKey.class));
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		when(validatorSet.containsKey(any())).thenReturn(false);
		epochManager.processEpochChange(new EpochChange(ancestor, validatorSet));

		assertThat(systemCounters.get(CounterType.EPOCH_MANAGER_QUEUED_CONSENSUS_EVENTS)).isEqualTo(0);
	}

	@Test
	public void when_next_epoch__then_get_vertices_rpc_should_be_forwarded_to_vertex_store() {
		when(vertexStore.getHighestQC()).thenReturn(mock(QuorumCertificate.class));

		BFTEventProcessor eventProcessor = mock(BFTEventProcessor.class);
		when(bftFactory.create(any(), any(), any(), any())).thenReturn(eventProcessor);

		Validator validator = mock(Validator.class);
		when(validator.nodeKey()).thenReturn(mock(ECPublicKey.class));
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		when(validatorSet.containsKey(any())).thenReturn(true);
		when(validatorSet.getValidators()).thenReturn(ImmutableSet.of(validator));
		epochManager.processEpochChange(new EpochChange(VertexMetadata.ofGenesisAncestor(), validatorSet));

		GetVerticesRequest getVerticesRequest = mock(GetVerticesRequest.class);
		epochManager.processGetVerticesRequest(getVerticesRequest);
		verify(vertexStore, times(1)).processGetVerticesRequest(eq(getVerticesRequest));

		GetVerticesResponse getVerticesResponse = mock(GetVerticesResponse.class);
		epochManager.processGetVerticesResponse(getVerticesResponse);
		verify(vertexStore, times(1)).processGetVerticesResponse(eq(getVerticesResponse));

		CommittedStateSync committedStateSync = mock(CommittedStateSync.class);
		epochManager.processCommittedStateSync(committedStateSync);
		verify(vertexStore, times(1)).processCommittedStateSync(eq(committedStateSync));
	}
}