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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.common.EUID;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.VertexStore;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.Ints;
import org.junit.Test;

/**
 * This tests that the {@link SafetyRules} implementation obeys HotStuff's safety and commit rules.
 */
public class SafetyRulesTest {
	private static final ECPublicKey SELF = makePubKey(EUID.ONE);
	private static final View GENESIS_VIEW = View.of(0);
	private static final AID GENESIS_ID = makeAID(1);

	private static SafetyRules createDefaultSafetyRules(VertexStore vertexStore) {
		ECKeyPair keyPair = mock(ECKeyPair.class);
		when(keyPair.getPublicKey()).thenReturn(SELF);
		when(keyPair.getUID()).thenReturn(EUID.ONE);
		RadixAddress address = mock(RadixAddress.class);
		when(address.getKey()).thenReturn(SELF);
		return new SafetyRules(address, keyPair, vertex -> Hash.ZERO_HASH, vertexStore, SafetyState.initialState());
	}

	private static ECPublicKey makePubKey(EUID id) {
		ECPublicKey pubKey = mock(ECPublicKey.class);
		when(pubKey.getUID()).thenReturn(id);
		return pubKey;
	}

	private static AID makeAID(int n) {
		byte[] temp = new byte[AID.BYTES];
		Ints.copyTo(n, temp, AID.BYTES - Integer.BYTES);
		return AID.from(temp);
	}

	@Test
	public void testLockedView() {
		/*
		 * This test ensures that the locking logic is working correctly.
		 * The locked view in HotStuff is the highest 2-chain head a node has seen.
		 */

		VertexStore vertexStore = new VertexStore();
		SafetyRules safetyRules = createDefaultSafetyRules(vertexStore);
		assertThat(safetyRules.getState().getLastVotedView()).isEqualByComparingTo(View.of(0L));
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(View.of(0L));

		AID a1Id = makeAID(11);
		AID a2Id = makeAID(12);
		AID a3Id = makeAID(13);
		AID a4Id = makeAID(14);
		AID b1Id = makeAID(21);
		AID b2Id = makeAID(22);
		AID b3Id = makeAID(23);
		AID b4Id = makeAID(24);
		Vertex a1 = makeVertex(makeGenesisVertex(vertexStore), View.of(1), a1Id, vertexStore);
		Vertex b1 = makeVertex(makeGenesisVertex(vertexStore), View.of(2), b1Id, vertexStore);
		Vertex b2 = makeVertex(a1, View.of(3), b2Id, vertexStore);
		Vertex a2 = makeVertex(b1, View.of(4), a2Id, vertexStore);
		Vertex b3 = makeVertex(a2, View.of(5), b3Id, vertexStore);
		Vertex a3 = makeVertex(a2, View.of(6), a3Id, vertexStore);
		Vertex a4 = makeVertex(a3, View.of(7), a4Id, vertexStore);
		Vertex b4 = makeVertex(b2, View.of(8), b4Id, vertexStore);

		safetyRules.process(a1);
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(GENESIS_VIEW);
		safetyRules.process(b1);
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(GENESIS_VIEW);
		safetyRules.process(b2);
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(GENESIS_VIEW);
		safetyRules.process(a2);
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(GENESIS_VIEW);
		safetyRules.process(a3);
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(b1.getView());
		safetyRules.process(b3);
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(b1.getView());
		safetyRules.process(a4);
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(a2.getView());
		safetyRules.process(b4);
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(a2.getView());
	}

	@Test
	public void testVote() throws SafetyViolationException, CryptoException {
		/*
		 * This test ensures that the voting logic obeys the safety rules correctly.
		 *
		 */

		VertexStore vertexStore = new VertexStore();
		SafetyRules safetyRules = createDefaultSafetyRules(vertexStore);
		assertThat(safetyRules.getState().getLastVotedView()).isEqualByComparingTo(View.of(0L));
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(View.of(0L));

		AID a1Id = makeAID(11);
		AID a2Id = makeAID(12);
		AID a3Id = makeAID(13);
		AID a4Id = makeAID(14);
		AID a5Id = makeAID(15);
		AID b1Id = makeAID(21);
		AID b2Id = makeAID(22);
		AID b3Id = makeAID(23);
		AID b4Id = makeAID(24);
		Vertex a1 = makeVertex(makeGenesisVertex(vertexStore), View.of(1), a1Id, vertexStore);
		Vertex b1 = makeVertex(makeGenesisVertex(vertexStore), View.of(2), b1Id, vertexStore);
		Vertex b2 = makeVertex(a1, View.of(3), b2Id, vertexStore);
		Vertex a2 = makeVertex(b1, View.of(4), a2Id, vertexStore);
		Vertex a3 = makeVertex(a2, View.of(5), a3Id, vertexStore);
		Vertex b3 = makeVertex(a2, View.of(6), b3Id, vertexStore);
		Vertex a4 = makeVertex(a3, View.of(7), a4Id, vertexStore);
		Vertex b4 = makeVertex(b2, View.of(8), b4Id, vertexStore);
		Vertex a5 = makeVertex(a4, View.of(9), a5Id, vertexStore);
		
		assertThat(safetyRules.process(a1)).isEmpty();

		assertThat(safetyRules.process(b1)).isEmpty();

		assertThat(safetyRules.process(a2)).isEmpty();

		safetyRules.process(b2);
		assertThatThrownBy(() -> safetyRules.voteFor(b2));

		assertThat(safetyRules.process(a3)).isEmpty();

		assertThat(safetyRules.process(b3)).isEmpty();

		assertThat(safetyRules.process(a4)).isEmpty();

		safetyRules.process(a4);
		assertThatThrownBy(() -> safetyRules.voteFor(a4));

		safetyRules.process(b4);
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

		VertexStore vertexStore = new VertexStore();
		SafetyRules safetyRules = createDefaultSafetyRules(vertexStore);
		assertThat(safetyRules.getState().getLastVotedView()).isEqualByComparingTo(View.of(0L));
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(View.of(0L));

		AID a1Id = makeAID(11);
		AID a2Id = makeAID(12);
		AID a3Id = makeAID(13);
		AID a4Id = makeAID(14);
		AID a5Id = makeAID(15);
		AID a6Id = makeAID(16);
		AID b1Id = makeAID(21);
		AID b2Id = makeAID(22);
		AID b3Id = makeAID(23);
		Vertex a1 = makeVertex(makeGenesisVertex(vertexStore), View.of(1), a1Id, vertexStore);
		Vertex b1 = makeVertex(makeGenesisVertex(vertexStore), View.of(2), b1Id, vertexStore);
		Vertex b2 = makeVertex(a1, View.of(3), b2Id, vertexStore);
		Vertex a2 = makeVertex(b1, View.of(4), a2Id, vertexStore);
		Vertex b3 = makeVertex(a2, View.of(5), b3Id, vertexStore);
		Vertex a3 = makeVertex(a2, View.of(6), a3Id, vertexStore);
		Vertex a4 = makeVertex(a3, View.of(7), a4Id, vertexStore);
		Vertex a5 = makeVertex(a4, View.of(8), a5Id, vertexStore);
		Vertex a6 = makeVertex(a5, View.of(9), a6Id, vertexStore);

		assertThat(safetyRules.process(a1)).isEmpty();
		assertThat(safetyRules.process(b1)).isEmpty();
		assertThat(safetyRules.process(b2)).isEmpty();
		assertThat(safetyRules.process(a2)).isEmpty();
		assertThat(safetyRules.process(b3)).isEmpty();
		assertThat(safetyRules.process(a3)).isEmpty();
		assertThat(safetyRules.process(a4)).isEmpty();
		assertThat(safetyRules.process(a5)).isEmpty();
		assertThat(safetyRules.process(a6)).hasValue(a3.getAID());
	}

	private static Vertex makeGenesisVertex(VertexStore vertexStore) {
		VertexMetadata genesisMetadata = new VertexMetadata(GENESIS_VIEW, GENESIS_ID);
		QuorumCertificate genesisQC = new QuorumCertificate(genesisMetadata, new ECDSASignatures());
		return makeVertex(genesisQC, GENESIS_VIEW, GENESIS_ID, vertexStore);
	}

	private static Vertex makeVertex(Vertex parent, View view, AID id, VertexStore vertexStore) {
		VertexMetadata parentMetadata = new VertexMetadata(parent.getView(), parent.getAID());
		QuorumCertificate qc = new QuorumCertificate(parentMetadata, new ECDSASignatures());
		return makeVertex(qc, view, id, vertexStore);
	}

	private static Vertex makeVertex(QuorumCertificate qc, View view, AID id, VertexStore vertexStore) {
		Atom atom = mock(Atom.class);
		when(atom.getAID()).thenReturn(id);
		Vertex vertex = new Vertex(qc, view, atom);
		vertexStore.insertVertex(vertex);
		return vertex;
	}
}
