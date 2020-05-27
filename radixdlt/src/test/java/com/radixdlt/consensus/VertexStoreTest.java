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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.sync.SyncedRadixEngine;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.Hash;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.CommittedAtom;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

public class VertexStoreTest {
	private Vertex genesisVertex;
	private VertexMetadata genesisVertexMetadata;
	private QuorumCertificate rootQC;
	private VertexStore vertexStore;
	private SyncedRadixEngine stateSynchronizer;

	@Before
	public void setUp() {
		this.genesisVertex = Vertex.createGenesis(null);
		this.genesisVertexMetadata = new VertexMetadata(View.genesis(), genesisVertex.getId(), 0);
		VoteData voteData = new VoteData(genesisVertexMetadata, null);
		this.rootQC = new QuorumCertificate(voteData, new ECDSASignatures());
		this.stateSynchronizer = mock(SyncedRadixEngine.class);
		SystemCounters counters = mock(SystemCounters.class);
		this.vertexStore = new VertexStore(genesisVertex, rootQC, stateSynchronizer, counters);
	}

	/*
	@Test
	public void when_vertex_retriever_succeeds__then_vertex_is_inserted() {
		Vertex vertex = Vertex.createVertex(rootQC, View.of(1), mock(Atom.class));
		VoteData voteData = new VoteData(VertexMetadata.ofVertex(vertex), genesisVertexMetadata);
		QuorumCertificate qc = new QuorumCertificate(voteData, new ECDSASignatures());
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getId()).thenReturn(vertex.getId());

		ECPublicKey author = mock(ECPublicKey.class);
		when(vertexSupplier.getVertex(eq(vertex.getId()), eq(author))).thenReturn(Single.just(vertex));
		vertexStore.syncToQC(qc);

		assertThat(vertexStore.getHighestQC()).isEqualTo(qc);
	}

	@Test
	public void when_vertex_retriever_fails_on_qc_sync__then_sync_exception_is_thrown() {
		QuorumCertificate qc = mock(QuorumCertificate.class);
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		Hash vertexId = mock(Hash.class);
		when(vertexMetadata.getId()).thenReturn(vertexId);
		when(qc.getProposed()).thenReturn(vertexMetadata);
		when(qc.getParent()).thenReturn(genesisVertexMetadata);

		ECPublicKey author = mock(ECPublicKey.class);
		when(vertexSupplier.getVertex(eq(vertexId), eq(author))).thenReturn(Single.error(new RuntimeException()));
		assertThatThrownBy(() -> vertexStore.syncToQC(qc))
			.isInstanceOf(SyncException.class);
	}
	*/

	@Test
	public void when_inserting_vertex_with_missing_parent__then_missing_parent_exception_is_thrown() {
		VertexMetadata vertexMetadata = new VertexMetadata(View.genesis(), Hash.ZERO_HASH, 0);
		VoteData voteData = new VoteData(vertexMetadata, null);
		QuorumCertificate qc = new QuorumCertificate(voteData, new ECDSASignatures());
		Vertex nextVertex = Vertex.createVertex(qc, View.of(1), mock(ClientAtom.class));
		assertThatThrownBy(() -> vertexStore.insertVertex(nextVertex))
			.isInstanceOf(MissingParentException.class);
	}

	@Test
	public void when_committing_vertex_which_was_not_inserted__then_illegal_state_exception_is_thrown() {
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getId()).thenReturn(mock(Hash.class));
		assertThatThrownBy(() -> vertexStore.commitVertex(vertexMetadata))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void when_insert_vertex__then_it_should_not_be_committed_or_stored_in_engine()
		throws VertexInsertionException, RadixEngineException {
		Vertex nextVertex = Vertex.createVertex(rootQC, View.of(1), null);
		vertexStore.insertVertex(nextVertex);

		TestObserver<Vertex> testObserver = TestObserver.create();
		vertexStore.lastCommittedVertex().subscribe(testObserver);
		testObserver.awaitCount(1); // genesis only
		testObserver.assertValues(genesisVertex); // not committed
		verify(stateSynchronizer, times(0)).storeAtom(any()); // not stored
	}

	@Test
	public void when_insert_and_commit_vertex__then_it_should_be_committed_and_stored_in_engine()
		throws VertexInsertionException, RadixEngineException {
		ClientAtom clientAtom = mock(ClientAtom.class);
		Vertex nextVertex = Vertex.createVertex(rootQC, View.of(1), clientAtom);
		vertexStore.insertVertex(nextVertex);
		TestObserver<Vertex> testObserver = TestObserver.create();
		vertexStore.lastCommittedVertex().subscribe(testObserver);
		testObserver.awaitCount(1); // genesis first

		VertexMetadata vertexMetadata = VertexMetadata.ofVertex(nextVertex);
		CommittedAtom committedAtom = mock(CommittedAtom.class);
		when(clientAtom.committed(eq(vertexMetadata))).thenReturn(committedAtom);
		assertThat(vertexStore.commitVertex(vertexMetadata)).isEqualTo(nextVertex);
		testObserver.awaitCount(2); // both committed
		testObserver.assertValues(genesisVertex, nextVertex); // both committed

		verify(stateSynchronizer, times(1))
			.storeAtom(eq(committedAtom)); // next atom stored
	}

	@Test
	public void when_insert_two_vertices__then_get_path_from_root_should_return_the_two_vertices()
		throws Exception {
		Vertex nextVertex0 = Vertex.createVertex(rootQC, View.of(1), null);
		VertexMetadata vertexMetadata = new VertexMetadata(View.of(1), nextVertex0.getId(), 1);
		VoteData voteData = new VoteData(vertexMetadata, rootQC.getProposed());
		QuorumCertificate qc = new QuorumCertificate(voteData, new ECDSASignatures());
		Vertex nextVertex1 = Vertex.createVertex(qc, View.of(2), null);
		vertexStore.insertVertex(nextVertex0);
		vertexStore.insertVertex(nextVertex1);
		assertThat(vertexStore.getPathFromRoot(nextVertex1.getId()))
			.isEqualTo(Arrays.asList(nextVertex1, nextVertex0));
	}

	@Test
	public void when_insert_and_commit_vertex__then_committed_vertex_should_emit_and_store_should_have_size_1()
		throws Exception {
		Vertex nextVertex = Vertex.createVertex(rootQC, View.of(1), null);
		vertexStore.insertVertex(nextVertex);

		TestObserver<Vertex> testObserver = TestObserver.create();
		vertexStore.lastCommittedVertex().subscribe(testObserver);
		testObserver.awaitCount(1);

		VertexMetadata vertexMetadata = VertexMetadata.ofVertex(nextVertex);
		assertThat(vertexStore.commitVertex(vertexMetadata)).isEqualTo(nextVertex);
		testObserver.awaitCount(2);
		testObserver.assertValues(genesisVertex, nextVertex);
		assertThat(vertexStore.getSize()).isEqualTo(1);
	}

	@Test
	public void when_insert_and_commit_vertex_2x__then_committed_vertex_should_emit_in_order_and_store_should_have_size_1()
		throws Exception {
		TestObserver<Vertex> testObserver = TestObserver.create();
		vertexStore.lastCommittedVertex().subscribe(testObserver);
		Vertex nextVertex = Vertex.createVertex(rootQC, View.of(1), null);
		vertexStore.insertVertex(nextVertex);
		VertexMetadata vertexMetadata = VertexMetadata.ofVertex(nextVertex);
		vertexStore.commitVertex(vertexMetadata);

		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getProposed()).thenReturn(VertexMetadata.ofVertex(nextVertex));
		Vertex nextVertex2 = Vertex.createVertex(qc, View.of(2), null);
		vertexStore.insertVertex(nextVertex2);
		VertexMetadata vertexMetadata2 = VertexMetadata.ofVertex(nextVertex2);
		vertexStore.commitVertex(vertexMetadata2);

		testObserver.awaitCount(3);
		testObserver.assertValues(genesisVertex, nextVertex, nextVertex2);
		assertThat(vertexStore.getSize()).isEqualTo(1);
	}

	@Test
	public void when_insert_two_and_commit_vertex__then_two_committed_vertices_should_emit_in_order_and_store_should_have_size_1()
		throws Exception {
		Vertex nextVertex = Vertex.createVertex(rootQC, View.of(1), null);
		vertexStore.insertVertex(nextVertex);

		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getProposed()).thenReturn(VertexMetadata.ofVertex(nextVertex));
		Vertex nextVertex2 = Vertex.createVertex(qc, View.of(2), null);
		vertexStore.insertVertex(nextVertex2);

		TestObserver<Vertex> testObserver = TestObserver.create();
		vertexStore.lastCommittedVertex().subscribe(testObserver);
		testObserver.awaitCount(1);

		VertexMetadata vertexMetadata2 = VertexMetadata.ofVertex(nextVertex2);
		vertexStore.commitVertex(vertexMetadata2);
		testObserver.awaitCount(3);
		testObserver.assertValues(genesisVertex, nextVertex, nextVertex2);
		assertThat(vertexStore.getSize()).isEqualTo(1);
	}
}