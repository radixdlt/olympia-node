package com.radixdlt.consensus;

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
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.liveness.ScheduledTimeoutSender;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.mempool.Mempool;
import org.junit.Test;

public class EpochManagerTest {
	@Test
	public void when_next_epoch_does_not_contain_self__then_should_not_emit_any_consensus_events() {
		ECKeyPair keyPair = mock(ECKeyPair.class);
		ECPublicKey publicKey = mock(ECPublicKey.class);
		when(keyPair.getPublicKey()).thenReturn(publicKey);

		BFTEventSender bftEventSender = mock(BFTEventSender.class);
		ScheduledTimeoutSender scheduledTimeoutSender = mock(ScheduledTimeoutSender.class);

		EpochManager epochManager = new EpochManager(
			mock(Mempool.class),
			bftEventSender,
			scheduledTimeoutSender,
			timeoutSender -> mock(Pacemaker.class),
			mock(VertexStoreFactory.class),
			proposers -> mock(ProposerElection.class),
			mock(Hasher.class),
			keyPair,
			mock(SystemCounters.class)
		);
		EpochChange epochChange = mock(EpochChange.class);
		ValidatorSet validatorSet = mock(ValidatorSet.class);
		when(validatorSet.containsKey(eq(publicKey))).thenReturn(false);
		when(epochChange.getValidatorSet()).thenReturn(validatorSet);
		epochManager.processEpochChange(epochChange);

		verify(bftEventSender, never()).sendNewView(any(), any());
		verify(bftEventSender, never()).sendVote(any(), any());
		verify(bftEventSender, never()).broadcastProposal(any());
		verify(scheduledTimeoutSender, never()).scheduleTimeout(any(), anyLong());
	}

	@Test
	public void when_no_epoch_change__then_processing_events_should_not_fail() {
		ECKeyPair keyPair = mock(ECKeyPair.class);
		EpochManager epochManager = new EpochManager(
			mock(Mempool.class),
			mock(BFTEventSender.class),
			mock(ScheduledTimeoutSender.class),
			timeoutSender -> mock(Pacemaker.class),
			mock(VertexStoreFactory.class),
			proposers -> mock(ProposerElection.class),
			mock(Hasher.class),
			keyPair,
			mock(SystemCounters.class)
		);
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
	public void when_next_epoch__then_get_vertices_rpc_should_be_forwarded_to_vertex_store() {
		ECKeyPair keyPair = mock(ECKeyPair.class);
		when(keyPair.getPublicKey()).thenReturn(mock(ECPublicKey.class));
		VertexStore vertexStore = mock(VertexStore.class);
		when(vertexStore.getHighestQC()).thenReturn(mock(QuorumCertificate.class));

		EpochManager epochManager = new EpochManager(
			mock(Mempool.class),
			mock(BFTEventSender.class),
			mock(ScheduledTimeoutSender.class),
			timeoutSender -> mock(Pacemaker.class),
			(v, qc) -> vertexStore,
			proposers -> mock(ProposerElection.class),
			mock(Hasher.class),
			keyPair,
			mock(SystemCounters.class)
		);

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