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

package com.radixdlt.consensus.liveness;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.ClientAtom;
import java.util.Collections;
import org.junit.Test;

public class MempoolProposalGeneratorTest {
	@Test
	public void when_vertex_store_contains_vertices_with_no_atom__then_generate_proposal_should_still_work() {
		Mempool mempool = mock(Mempool.class);
		ClientAtom reAtom = mock(ClientAtom.class);
		when(mempool.getAtoms(anyInt(), anySet())).thenReturn(Collections.singletonList(reAtom));

		VertexStore vertexStore = mock(VertexStore.class);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getProposed()).thenReturn(mock(VertexMetadata.class));
		when(vertexStore.getHighestQC()).thenReturn(qc);
		Vertex vertex = mock(Vertex.class);
		when(vertex.getAtom()).thenReturn(null);
		when(vertexStore.getPathFromRoot(any())).thenReturn(Collections.singletonList(vertex));

		MempoolProposalGenerator proposalGenerator = new MempoolProposalGenerator(vertexStore, mempool);
		Vertex proposal = proposalGenerator.generateProposal(View.of(1));
		assertThat(proposal.getAtom()).isEqualTo(reAtom);
	}
}