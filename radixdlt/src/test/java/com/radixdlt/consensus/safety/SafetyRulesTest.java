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

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.common.EUID;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexInsertionException;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.VertexStore;
import com.radixdlt.consensus.View;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This tests that the {@link SafetyRules} implementation obeys HotStuff's safety and commit rules.
 */
public class SafetyRulesTest {
	private static final ECPublicKey SELF = makePubKey(EUID.ONE);
	private static final View GENESIS_VIEW = View.of(0);
	private static final Vertex GENESIS_VERTEX = Vertex.createGenesis(null);

	private static SafetyRules createDefaultSafetyRules(VertexStore vertexStore) {
		ECKeyPair keyPair = mock(ECKeyPair.class);
		when(keyPair.getPublicKey()).thenReturn(SELF);
		when(keyPair.getUID()).thenReturn(EUID.ONE);
		RadixAddress address = mock(RadixAddress.class);
		when(address.getKey()).thenReturn(SELF);
		return new SafetyRules(keyPair, vertexStore, SafetyState.initialState());
	}

	private static ECPublicKey makePubKey(EUID id) {
		ECPublicKey pubKey = mock(ECPublicKey.class);
		when(pubKey.getUID()).thenReturn(id);
		return pubKey;
	}

	@Test
	public void testLockedView() {
		/*
		 * This test ensures that locking works correctly.
		 * The locked view in HotStuff is the highest consecutive 2-chain head a node has seen.
		 */

		VertexStore vertexStore = makeVertexStore();
		SafetyRules safetyRules = createDefaultSafetyRules(vertexStore);
		assertThat(safetyRules.getState().getLastVotedView()).isEqualByComparingTo(View.of(0L));
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(View.of(0L));

		Vertex a1 = makeVertex(GENESIS_VERTEX, View.of(1), vertexStore);
		Vertex b1 = makeVertex(GENESIS_VERTEX, View.of(2), vertexStore);
		Vertex b2 = makeVertex(a1, View.of(3), vertexStore);
		Vertex a2 = makeVertex(b1, View.of(4), vertexStore);
		Vertex b3 = makeVertex(a2, View.of(5), vertexStore);
		Vertex a3 = makeVertex(a2, View.of(6), vertexStore);
		Vertex a4 = makeVertex(a3, View.of(7), vertexStore);
		Vertex a5 = makeVertex(a4, View.of(8), vertexStore);
		Vertex a6 = makeVertex(a5, View.of(9), vertexStore);

		safetyRules.process(a1);
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(GENESIS_VIEW);
		safetyRules.process(b1);
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(GENESIS_VIEW);
		safetyRules.process(b2);
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(GENESIS_VIEW);
		safetyRules.process(a2);
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(GENESIS_VIEW);
		safetyRules.process(a3);
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(GENESIS_VIEW);
		safetyRules.process(b3);
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(GENESIS_VIEW);
		safetyRules.process(a4);
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(GENESIS_VIEW);
		safetyRules.process(a5);
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(a3.getView());
		safetyRules.process(a6);
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(a4.getView());
	}

	@Test
	public void testVote() throws SafetyViolationException, CryptoException {
		/*
		 * This test ensures that voting is safe.
		 */

		VertexStore vertexStore = makeVertexStore();
		SafetyRules safetyRules = createDefaultSafetyRules(vertexStore);
		assertThat(safetyRules.getState().getLastVotedView()).isEqualByComparingTo(View.of(0L));
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(View.of(0L));

		Vertex a1 = makeVertex(GENESIS_VERTEX, View.of(1), vertexStore);
		Vertex b1 = makeVertex(GENESIS_VERTEX, View.of(2), vertexStore);
		Vertex b2 = makeVertex(a1, View.of(3), vertexStore);
		Vertex a2 = makeVertex(b1, View.of(4), vertexStore);
		Vertex a3 = makeVertex(a2, View.of(5), vertexStore);
		Vertex b3 = makeVertex(a2, View.of(6), vertexStore);
		Vertex a4 = makeVertex(a3, View.of(7), vertexStore);
		Vertex b4 = makeVertex(b2, View.of(8), vertexStore);

		assertThat(safetyRules.process(a1)).isEmpty();
		safetyRules.voteFor(a1);

		assertThat(safetyRules.process(b1)).isEmpty();
		safetyRules.voteFor(b1);

		assertThat(safetyRules.process(a2)).isEmpty();
		safetyRules.voteFor(a2);

		safetyRules.process(b2);
		assertThatThrownBy(() -> safetyRules.voteFor(b2));

		assertThat(safetyRules.process(a3)).isEmpty();
		safetyRules.voteFor(a3);

		assertThat(safetyRules.process(b3)).isEmpty();
		safetyRules.voteFor(b3);

		assertThat(safetyRules.process(a4)).isEmpty();
		safetyRules.voteFor(a4);

		safetyRules.process(a4);
		assertThatThrownBy(() -> safetyRules.voteFor(a4));

		safetyRules.process(b4);
		assertThatThrownBy(() -> safetyRules.voteFor(b4));
	}

	@Test
	public void testCommitRule() {
		/*
		 * This test ensures that the commit logic is working correctly.
		 * The commit rule requires a consecutive 3-chain to commit an atom, that is, the chain
		 *  A2 -> A3 -> A4 -> A5
		 * would allow A2 to be committed at the time A5's QC for A4 is presented.
		 */

		VertexStore vertexStore = makeVertexStore();
		SafetyRules safetyRules = createDefaultSafetyRules(vertexStore);
		assertThat(safetyRules.getState().getLastVotedView()).isEqualByComparingTo(View.of(0L));
		assertThat(safetyRules.getState().getLockedView()).isEqualByComparingTo(View.of(0L));

		Vertex a1 = makeVertex(GENESIS_VERTEX, View.of(1), vertexStore);
		Vertex b1 = makeVertex(GENESIS_VERTEX, View.of(2), vertexStore);
		Vertex b2 = makeVertex(a1, View.of(3), vertexStore);
		Vertex a2 = makeVertex(b1, View.of(4), vertexStore);
		Vertex b3 = makeVertex(a2, View.of(5), vertexStore);
		Vertex a3 = makeVertex(a2, View.of(6), vertexStore);
		Vertex a4 = makeVertex(a3, View.of(7), vertexStore);
		Vertex a5 = makeVertex(a4, View.of(8), vertexStore);
		Vertex a6 = makeVertex(a5, View.of(9), vertexStore);

		assertThat(safetyRules.process(a1)).isEmpty();
		assertThat(safetyRules.process(b1)).isEmpty();
		assertThat(safetyRules.process(b2)).isEmpty();
		assertThat(safetyRules.process(a2)).isEmpty();
		assertThat(safetyRules.process(b3)).isEmpty();
		assertThat(safetyRules.process(a3)).isEmpty();
		assertThat(safetyRules.process(a4)).isEmpty();
		assertThat(safetyRules.process(a5)).isEmpty();
		assertThat(safetyRules.process(a5)).isEmpty();
		assertThat(safetyRules.process(a6)).hasValue(a3.getId());
	}

	private static VertexStore makeVertexStore() {
		final VertexMetadata genesisMetadata = new VertexMetadata(View.genesis(), GENESIS_VERTEX.getId(), View.genesis(), GENESIS_VERTEX.getId());
		final QuorumCertificate rootQC = new QuorumCertificate(genesisMetadata, new ECDSASignatures());
		return new VertexStore(GENESIS_VERTEX, rootQC, mock(RadixEngine.class));
	}

	private static Vertex makeVertex(Vertex parent, View view, VertexStore vertexStore) {
		VertexMetadata parentMetadata = new VertexMetadata(
			parent.getView(),
			parent.getId(),
			parent.getParentView(),
			parent.getParentId()
		);
		QuorumCertificate qc = new QuorumCertificate(parentMetadata, new ECDSASignatures());
		Vertex vertex = Vertex.createVertex(qc, view, null);
		try {
			vertexStore.insertVertex(vertex);
		} catch (VertexInsertionException e) {
			throw new RuntimeException("Failed to setup vertex " + vertex, e);
		}
		return vertex;
	}
}
