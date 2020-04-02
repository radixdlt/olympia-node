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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.radixdlt.atommodel.Atom;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.Hash;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class VertexStoreTest {
	private Vertex genesisVertex;
	private QuorumCertificate rootQC;
	private VertexStore vertexStore;
	private RadixEngine radixEngine;


	@Before
	public void setUp() {
		this.genesisVertex = Vertex.createGenesis(null);
		VertexMetadata vertexMetadata = new VertexMetadata(
			View.genesis(), genesisVertex.getId(), null, null
		);
		this.rootQC = new QuorumCertificate(vertexMetadata, new ECDSASignatures());
		this.radixEngine = mock(RadixEngine.class);
		this.vertexStore = new VertexStore(genesisVertex, rootQC, radixEngine);
	}

	@Test
	public void when_qc_sync_fails__then_sync_exception_is_thrown() {
		QuorumCertificate qc = mock(QuorumCertificate.class);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getId()).thenReturn(mock(Hash.class));
		when(qc.getVertexMetadata()).thenReturn(vertexMetadata);
		assertThatThrownBy(() -> vertexStore.syncToQC(qc))
			.isInstanceOf(SyncException.class);
	}

	@Test
	public void when_inserting_vertex_with_missing_parent__then_missing_parent_exception_is_thrown() throws Exception {
		VertexMetadata vertexMetadata = new VertexMetadata(
			View.genesis(), Hash.ZERO_HASH, null, null
		);
		QuorumCertificate qc = new QuorumCertificate(vertexMetadata, new ECDSASignatures());
		Vertex nextVertex = Vertex.createVertex(qc, View.of(1), mock(Atom.class));
		assertThatThrownBy(() -> vertexStore.insertVertex(nextVertex))
			.isInstanceOf(MissingParentException.class);
	}

	@Test
	@Ignore("Reinstate once better ProposalGenerator + Mempool is implemented")
	public void when_inserting_vertex_which_fails_to_pass_re__then_vertex_insertion_exception_is_thrown() throws Exception {
		doThrow(mock(RadixEngineException.class)).when(radixEngine).store(any());

		Vertex nextVertex = Vertex.createVertex(rootQC, View.of(1), mock(Atom.class));
		assertThatThrownBy(() -> vertexStore.insertVertex(nextVertex))
			.isInstanceOf(VertexInsertionException.class);
	}

	@Test
	public void when_committing_vertex_which_was_not_inserted__then_illegal_state_exception_is_thrown() {
		assertThatThrownBy(() -> vertexStore.commitVertex(mock(Hash.class)))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void when_insert_two_vertices__then_get_path_from_root_should_return_the_two_vertices() throws Exception {
		Vertex nextVertex0 = Vertex.createVertex(rootQC, View.of(1), null);
		VertexMetadata vertexMetadata = new VertexMetadata(View.of(1), nextVertex0.getId(), View.genesis(), genesisVertex.getId());
		QuorumCertificate qc = new QuorumCertificate(vertexMetadata, new ECDSASignatures());
		Vertex nextVertex1 = Vertex.createVertex(qc, View.of(2), null);
		vertexStore.insertVertex(nextVertex0);
		vertexStore.insertVertex(nextVertex1);
		assertThat(vertexStore.getPathFromRoot(nextVertex1.getId()))
			.isEqualTo(Arrays.asList(nextVertex1, nextVertex0));
	}

	@Test
	public void when_insert_and_commit_vertex__then_committed_vertex_should_emit() throws Exception {
		Vertex nextVertex = Vertex.createVertex(rootQC, View.of(1), null);
		vertexStore.insertVertex(nextVertex);

		TestObserver<Vertex> testObserver = TestObserver.create();
		vertexStore.lastCommittedVertex().subscribe(testObserver);
		testObserver.awaitCount(1);

		assertThat(vertexStore.commitVertex(nextVertex.getId())).isEqualTo(nextVertex);
		testObserver.awaitCount(2);
		testObserver.assertValues(genesisVertex, nextVertex);
	}
}