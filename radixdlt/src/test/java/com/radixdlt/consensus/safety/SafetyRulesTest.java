/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.safety;

import com.radixdlt.consensus.DefaultHasher;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.safety.SafetyState.Builder;
import com.radixdlt.crypto.Hash;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECKeyPair;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This tests that the {@link SafetyRules} implementation obeys HotStuff's safety and commit rules.
 */
public class SafetyRulesTest {
	private static final VertexMetadata genesisAncestor = VertexMetadata.ofGenesisAncestor();
	private static final VoteData GENESIS_DATA = new VoteData(genesisAncestor, genesisAncestor, null);
	private static final QuorumCertificate GENESIS_QC = new QuorumCertificate(GENESIS_DATA, new TimestampedECDSASignatures());

	private SafetyState safetyState;
	private SafetyRules safetyRules;

	@Before
	public void setup() {
		this.safetyState = mock(SafetyState.class);
		this.safetyRules = new SafetyRules(mock(BFTNode.class), safetyState, new DefaultHasher(), ECKeyPair.generateNew()::sign);
	}

	@Test
	public void when_vote_on_same_view__then_exception_is_thrown() {
		View view = mock(View.class);
		when(safetyState.getLastVotedView()).thenReturn(view);
		Vertex vertex = mock(Vertex.class);
		when(vertex.getView()).thenReturn(view);

		assertThatThrownBy(() -> this.safetyRules.voteFor(vertex, mock(VertexMetadata.class), 0L, 0L))
			.isInstanceOf(SafetyViolationException.class);
	}

	@Test
	public void when_vote_with_qc_on_different_locked_view__then_exception_is_thrown() {
		when(safetyState.getLastVotedView()).thenReturn(View.of(2));
		when(safetyState.getLockedView()).thenReturn(View.of(1));
		Vertex vertex = mock(Vertex.class);
		when(vertex.getView()).thenReturn(View.of(3));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.of(0));
		when(vertex.getQC()).thenReturn(qc);

		assertThatThrownBy(() -> this.safetyRules.voteFor(vertex, mock(VertexMetadata.class), 0L, 0L))
			.isInstanceOf(SafetyViolationException.class);
	}

	@Test
	public void when_vote_on_proposal_after_genesis__then_returned_vote_has_no_commit() throws SafetyViolationException {
		when(safetyState.getLastVotedView()).thenReturn(View.of(0));
		when(safetyState.getLockedView()).thenReturn(View.of(0));
		when(safetyState.toBuilder()).thenReturn(mock(Builder.class));
		Vertex vertex = Vertex.createVertex(GENESIS_QC, View.of(1), null);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		Vote vote = safetyRules.voteFor(vertex, vertexMetadata, 0L, 0L);
		assertThat(vote.getVoteData().getProposed()).isEqualTo(vertexMetadata);
		assertThat(vote.getVoteData().getParent()).isEqualTo(vertex.getQC().getProposed());
		assertThat(vote.getVoteData().getCommitted()).isEmpty();
	}

	@Test
	public void when_vote_on_proposal_two_after_genesis__then_returned_vote_has_no_commit() throws SafetyViolationException {
		when(safetyState.getLastVotedView()).thenReturn(View.of(1));
		when(safetyState.getLockedView()).thenReturn(View.of(0));
		when(safetyState.toBuilder()).thenReturn(mock(Builder.class));
		Vertex vertex = Vertex.createVertex(GENESIS_QC, View.of(1), null);
		VoteData voteData = new VoteData(VertexMetadata.ofVertex(vertex, false), vertex.getQC().getProposed(), null);
		QuorumCertificate qc = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		Vertex proposal = Vertex.createVertex(qc, View.of(2), null);
		Vote vote = safetyRules.voteFor(proposal, mock(VertexMetadata.class), 0L, 0L);
		assertThat(vote.getVoteData().getCommitted()).isEmpty();
	}

	@Test
	public void when_vote_on_proposal_three_after_genesis__then_returned_vote_has_commit() throws SafetyViolationException {
		when(safetyState.getLastVotedView()).thenReturn(View.of(1));
		when(safetyState.getLockedView()).thenReturn(View.of(0));
		when(safetyState.toBuilder()).thenReturn(mock(Builder.class));

		Vertex grandParent = Vertex.createVertex(GENESIS_QC, View.of(1), null);
		VoteData voteData = new VoteData(VertexMetadata.ofVertex(grandParent, false), grandParent.getQC().getProposed(), null);
		QuorumCertificate qc = new QuorumCertificate(voteData, new TimestampedECDSASignatures());

		Vertex parent = Vertex.createVertex(qc, View.of(2), null);
		VoteData parentVoteData = new VoteData(VertexMetadata.ofVertex(parent, false), parent.getQC().getProposed(), null);
		QuorumCertificate parentQC = new QuorumCertificate(parentVoteData, new TimestampedECDSASignatures());

		Vertex proposal = Vertex.createVertex(parentQC, View.of(3), null);

		Vote vote = safetyRules.voteFor(proposal, mock(VertexMetadata.class), 0L, 0L);
		assertThat(vote.getVoteData().getCommitted()).hasValue(VertexMetadata.ofVertex(grandParent, false));
	}

	@Test
	public void when_vote_on_proposal_three_after_genesis_with_skip__then_returned_vote_has_no_commit() throws SafetyViolationException {
		when(safetyState.getLastVotedView()).thenReturn(View.of(1));
		when(safetyState.getLockedView()).thenReturn(View.of(0));
		when(safetyState.toBuilder()).thenReturn(mock(Builder.class));

		Vertex grandParent = Vertex.createVertex(GENESIS_QC, View.of(1), null);
		VoteData voteData = new VoteData(VertexMetadata.ofVertex(grandParent, false), grandParent.getQC().getProposed(), null);
		QuorumCertificate qc = new QuorumCertificate(voteData, new TimestampedECDSASignatures());

		Vertex parent = Vertex.createVertex(qc, View.of(2), null);
		VoteData parentVoteData = new VoteData(VertexMetadata.ofVertex(parent, false), parent.getQC().getProposed(), null);
		QuorumCertificate parentQC = new QuorumCertificate(parentVoteData, new TimestampedECDSASignatures());

		Vertex proposal = Vertex.createVertex(parentQC, View.of(4), null);

		Vote vote = safetyRules.voteFor(proposal, mock(VertexMetadata.class), 0L, 0L);
		assertThat(vote.getVoteData().getCommitted()).isEmpty();
	}

	@Test
	public void when_process_qc_with_commit_greater_than_current__then_return_commit() {
		when(safetyState.getLastVotedView()).thenReturn(View.of(0));
		when(safetyState.getLockedView()).thenReturn(View.of(0));
		when(safetyState.getCommittedView()).thenReturn(View.of(0));
		when(safetyState.toBuilder()).thenReturn(mock(Builder.class));

		Hash toBeCommitted = mock(Hash.class);

		VoteData voteData = new VoteData(
			new VertexMetadata(0, View.of(3), mock(Hash.class), 3, false),
			new VertexMetadata(0, View.of(2), mock(Hash.class), 2, false),
			new VertexMetadata(0, View.of(1), toBeCommitted, 1, false)
		);

		QuorumCertificate qc = new QuorumCertificate(voteData, new TimestampedECDSASignatures());

		assertThat(safetyRules.process(qc)).hasValue(new VertexMetadata(0, View.of(1), toBeCommitted, 1, false));
	}
}
