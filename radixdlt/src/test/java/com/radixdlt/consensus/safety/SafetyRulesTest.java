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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.radixdlt.common.EUID;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Round;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.Vote;
import org.junit.Test;

/**
 * This tests that the {@link SafetyRules} implementation obeys HotStuff's safety and commit rules.
 */
public class SafetyRulesTest {
	private static final EUID SELF = EUID.ONE;
	private static final Round GENESIS_ROUND = Round.of(0);
	private static final Vertex GENESIS_VERTEX = makeGenesisVertex();

	private static SafetyRules createDefaultSafetyRules() {
		return new SafetyRules(SELF, SafetyState.initialState());
	}

	@Test
	public void testLockedRound() {
		/*
		 * This test ensures that the locking logic is working correctly.
		 * The locked round in HotStuff is the highest 2-chain head a node has seen.
		 */

		SafetyRules safetyRules = createDefaultSafetyRules();
		assertThat(safetyRules.getState().lastVotedRound).isEqualByComparingTo(Round.of(0L));
		assertThat(safetyRules.getState().lockedRound).isEqualByComparingTo(Round.of(0L));

		Vertex a1 = makeVertex(GENESIS_VERTEX, Round.of(1));
		Vertex b1 = makeVertex(GENESIS_VERTEX, Round.of(2));
		Vertex b2 = makeVertex(a1, Round.of(3));
		Vertex a2 = makeVertex(b1, Round.of(4));
		Vertex b3 = makeVertex(a2, Round.of(5));
		Vertex a3 = makeVertex(a2, Round.of(6));
		Vertex a4 = makeVertex(a3, Round.of(7));
		Vertex b4 = makeVertex(b2, Round.of(8));

		safetyRules.process(a1.getQC());
		assertThat(safetyRules.getState().lockedRound).isEqualByComparingTo(GENESIS_ROUND);
		safetyRules.process(b1.getQC());
		assertThat(safetyRules.getState().lockedRound).isEqualByComparingTo(GENESIS_ROUND);
		safetyRules.process(b2.getQC());
		assertThat(safetyRules.getState().lockedRound).isEqualByComparingTo(GENESIS_ROUND);
		safetyRules.process(a2.getQC());
		assertThat(safetyRules.getState().lockedRound).isEqualByComparingTo(GENESIS_ROUND);
		safetyRules.process(a3.getQC());
		assertThat(safetyRules.getState().lockedRound).isEqualByComparingTo(b1.getRound());
		safetyRules.process(b3.getQC());
		assertThat(safetyRules.getState().lockedRound).isEqualByComparingTo(b1.getRound());
		safetyRules.process(a4.getQC());
		assertThat(safetyRules.getState().lockedRound).isEqualByComparingTo(a2.getRound());
		safetyRules.process(b4.getQC());
		assertThat(safetyRules.getState().lockedRound).isEqualByComparingTo(a2.getRound());
	}

	@Test
	public void testVote() throws SafetyViolationException {
		/*
		 * This test ensures that the voting logic obeys the safety rules correctly.
		 *
		 */

		SafetyRules safetyRules = createDefaultSafetyRules();
		assertThat(safetyRules.getState().lastVotedRound).isEqualByComparingTo(Round.of(0L));
		assertThat(safetyRules.getState().lockedRound).isEqualByComparingTo(Round.of(0L));

		Vertex a1 = makeVertex(GENESIS_VERTEX, Round.of(1));
		Vertex b1 = makeVertex(GENESIS_VERTEX, Round.of(2));
		Vertex b2 = makeVertex(a1, Round.of(3));
		Vertex a2 = makeVertex(b1, Round.of(4));
		Vertex a3 = makeVertex(a2, Round.of(5));
		Vertex b3 = makeVertex(a2, Round.of(6));
		Vertex a4 = makeVertex(a3, Round.of(7));
		Vertex b4 = makeVertex(b2, Round.of(8));

		safetyRules.process(a1.getQC());
		VoteResult result = safetyRules.voteFor(a1);
		assertThat(result.getCommittedVertexId()).isEmpty();

		safetyRules.process(b1.getQC());
		result = safetyRules.voteFor(b1);
		assertThat(result.getCommittedVertexId()).isEmpty();

		safetyRules.process(a2.getQC());
		result = safetyRules.voteFor(a2);
		assertThat(result.getCommittedVertexId()).isEmpty();

		safetyRules.process(b2.getQC());
		assertThatThrownBy(() -> safetyRules.voteFor(b2));

		safetyRules.process(a3.getQC());
		result = safetyRules.voteFor(a3);
		assertThat(result.getCommittedVertexId()).isEmpty();

		safetyRules.process(b3.getQC());
		result = safetyRules.voteFor(b3);
		assertThat(result.getCommittedVertexId()).isEmpty();

		safetyRules.process(a4.getQC());
		result = safetyRules.voteFor(a4);
		assertThat(result.getCommittedVertexId()).isEmpty();

		safetyRules.process(a4.getQC());
		assertThatThrownBy(() -> safetyRules.voteFor(a4));

		safetyRules.process(b4.getQC());
		assertThatThrownBy(() -> safetyRules.voteFor(b4));
	}

	@Test
	public void testCommitRule() {
		/*
		 * This test ensures that the commit logic is working correctly.
		 * The commit rule requires a 3-chain to commit an atom, that is, the chain
		 *  A2 -> A3 -> A4 (-> A5)
		 * would allow A2 to be committed at the time A5's QC for A4 is presented.
		 */

		SafetyRules safetyRules = createDefaultSafetyRules();
		assertThat(safetyRules.getState().lastVotedRound).isEqualByComparingTo(Round.of(0L));
		assertThat(safetyRules.getState().lockedRound).isEqualByComparingTo(Round.of(0L));

		Vertex a1 = makeVertex(GENESIS_VERTEX, Round.of(1));
		Vertex b1 = makeVertex(GENESIS_VERTEX, Round.of(2));
		Vertex b2 = makeVertex(a1, Round.of(3));
		Vertex a2 = makeVertex(b1, Round.of(4));
		Vertex b3 = makeVertex(a2, Round.of(5));
		Vertex a3 = makeVertex(a2, Round.of(6));
		Vertex a4 = makeVertex(a3, Round.of(7));
		Vertex a5 = makeVertex(a4, Round.of(8));

		assertThat(safetyRules.getCommittedVertexId(a1)).isEqualTo(null);
		assertThat(safetyRules.getCommittedVertexId(b1)).isEqualTo(null);
		assertThat(safetyRules.getCommittedVertexId(b2)).isEqualTo(null);
		assertThat(safetyRules.getCommittedVertexId(a2)).isEqualTo(null);
		assertThat(safetyRules.getCommittedVertexId(b3)).isEqualTo(null);
		assertThat(safetyRules.getCommittedVertexId(a3)).isEqualTo(null);
		assertThat(safetyRules.getCommittedVertexId(a4)).isEqualTo(null);
		assertThat(safetyRules.getCommittedVertexId(a5)).isEqualTo(a3.getId());
	}

	private static Vertex makeGenesisVertex() {
		return Vertex.createGenesis(null);
	}

	private static Vertex makeVertex(Vertex parent, Round round) {
		VertexMetadata parentMetadata = new VertexMetadata(
			parent.getRound(),
			parent.getId(),
			parent.getParentRound(),
			parent.getParentId()
		);
		QuorumCertificate qc = new QuorumCertificate(new Vote(SELF, parentMetadata), parentMetadata);
		return Vertex.createVertex(qc, round, null);
	}
}
