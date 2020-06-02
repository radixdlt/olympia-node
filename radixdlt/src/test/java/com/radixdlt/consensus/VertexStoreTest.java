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
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.VertexStore.SyncSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.CommittedAtom;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;

public class VertexStoreTest {
	private Vertex genesisVertex;
	private VertexMetadata genesisVertexMetadata;
	private QuorumCertificate rootQC;
	private VertexStore vertexStore;
	private SyncedStateComputer<CommittedAtom> syncedStateComputer;
	private SyncSender syncSender;
	private SyncVerticesRPCSender syncVerticesRPCSender;
	private SystemCounters counters;

	@Before
	public void setUp() {
		this.genesisVertex = Vertex.createGenesis(null);
		this.genesisVertexMetadata = new VertexMetadata(View.genesis(), genesisVertex.getId(), 0);
		VoteData voteData = new VoteData(genesisVertexMetadata, null);
		this.rootQC = new QuorumCertificate(voteData, new ECDSASignatures());
		this.syncedStateComputer = mock(SyncedStateComputer.class);
		this.syncVerticesRPCSender = mock(SyncVerticesRPCSender.class);
		this.syncSender = mock(SyncSender.class);
		this.counters = mock(SystemCounters.class);
		this.vertexStore = new VertexStore(genesisVertex, rootQC, syncedStateComputer, syncVerticesRPCSender, syncSender, counters);
	}

	@Test
	public void when_vertex_store_created_with_incorrect_roots__then_exception_is_thrown() {
		Vertex nextVertex = Vertex.createVertex(rootQC, View.of(1), null);
		VertexMetadata nextVertexMetadata = VertexMetadata.ofVertex(nextVertex);

		VoteData voteData = new VoteData(nextVertexMetadata, genesisVertexMetadata);
		QuorumCertificate badRootQC = new QuorumCertificate(voteData, new ECDSASignatures());
		assertThatThrownBy(() -> new VertexStore(genesisVertex, badRootQC, syncedStateComputer, syncVerticesRPCSender, syncSender, counters))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void when_vertex_store_created_with_correct_vertices__then_exception_is_not_thrown() {
		Vertex nextVertex = Vertex.createVertex(rootQC, View.of(1), null);
		this.vertexStore = new VertexStore(
			genesisVertex,
			rootQC,
			Collections.singletonList(nextVertex),
			syncedStateComputer,
			syncVerticesRPCSender,
			syncSender,
			counters
		);
	}

	@Test
	public void when_vertex_store_created_with_incorrect_vertices__then_exception_is_thrown() {
		Vertex nextVertex = Vertex.createVertex(rootQC, View.of(1), null);
		VertexMetadata nextVertexMetadata = VertexMetadata.ofVertex(nextVertex);
		VoteData voteData = new VoteData(nextVertexMetadata, genesisVertexMetadata);
		QuorumCertificate nextQC = new QuorumCertificate(voteData, new ECDSASignatures());
		Vertex nextVertex2 = Vertex.createVertex(nextQC, View.of(2), null);

		assertThatThrownBy(() ->
			new VertexStore(
				genesisVertex,
				rootQC,
				Collections.singletonList(nextVertex2),
				syncedStateComputer,
				syncVerticesRPCSender,
				syncSender,
				counters
			)
		).isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void when_vertex_retriever_succeeds__then_vertex_is_inserted() {
		Vertex vertex = Vertex.createVertex(rootQC, View.of(1), null);
		VoteData voteData = new VoteData(VertexMetadata.ofVertex(vertex), genesisVertexMetadata);
		QuorumCertificate qc = new QuorumCertificate(voteData, new ECDSASignatures());
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getId()).thenReturn(vertex.getId());

		ECPublicKey author = mock(ECPublicKey.class);

		AtomicReference<Object> opaque = new AtomicReference<>();
		doAnswer(invocation -> {
			opaque.set(invocation.getArgument(3));
			return null;
		}).when(syncVerticesRPCSender).sendGetVerticesRequest(eq(vertex.getId()), any(), eq(1), any());

		assertThat(vertexStore.syncToQC(qc, vertexStore.getHighestCommittedQC(), author)).isFalse();
		verify(syncVerticesRPCSender, times(1)).sendGetVerticesRequest(eq(vertex.getId()), any(), eq(1), any());

		GetVerticesResponse getVerticesResponse = new GetVerticesResponse(vertex.getId(), Collections.singletonList(vertex), opaque.get());
		vertexStore.processGetVerticesResponse(getVerticesResponse);

		verify(syncSender, times(1)).synced(eq(vertex.getId()));
		assertThat(vertexStore.getHighestQC()).isEqualTo(qc);
	}

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
		when(vertexMetadata.getView()).thenReturn(View.of(2));
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
		testObserver.assertEmpty();
		verify(syncedStateComputer, times(0)).execute(any()); // not stored
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
		assertThat(vertexStore.commitVertex(vertexMetadata)).hasValue(nextVertex);
		testObserver.awaitCount(1); // both committed
		testObserver.assertValues(nextVertex);

		verify(syncedStateComputer, times(1))
			.execute(eq(committedAtom)); // next atom stored
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
		assertThat(vertexStore.commitVertex(vertexMetadata)).hasValue(nextVertex);
		testObserver.awaitCount(1);
		testObserver.assertValues(nextVertex);
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

		testObserver.awaitCount(2);
		testObserver.assertValues(nextVertex, nextVertex2);
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
		testObserver.awaitCount(2);
		testObserver.assertValues(nextVertex, nextVertex2);
		assertThat(vertexStore.getSize()).isEqualTo(1);
	}

	@Test
	public void when_sync_to_qc_which_doesnt_exist_and_vertex_is_inserted_later__then_sync_should_be_emitted() throws Exception {
		Vertex nextVertex = Vertex.createVertex(rootQC, View.of(1), null);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getProposed()).thenReturn(VertexMetadata.ofVertex(nextVertex));

		assertThat(vertexStore.syncToQC(qc, vertexStore.getHighestCommittedQC(), mock(ECPublicKey.class))).isFalse();
		vertexStore.insertVertex(nextVertex);
		verify(syncSender, times(1)).synced(eq(nextVertex.getId()));
	}

	@Test
	public void when_sync_to_qc_with_no_author_and_synced__then_should_return_true() throws Exception {
		Vertex nextVertex = Vertex.createVertex(rootQC, View.of(1), null);
		vertexStore.insertVertex(nextVertex);

		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.of(1));
		when(qc.getProposed()).thenReturn(VertexMetadata.ofVertex(nextVertex));
		assertThat(vertexStore.syncToQC(qc, vertexStore.getHighestCommittedQC(), null)).isTrue();
	}

	@Test
	public void when_sync_to_qc_with_no_author_and_not_synced__then_should_throw_illegal_state_exception() {
		Vertex nextVertex = Vertex.createVertex(rootQC, View.of(1), null);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getProposed()).thenReturn(VertexMetadata.ofVertex(nextVertex));

		assertThatThrownBy(() -> vertexStore.syncToQC(qc, vertexStore.getHighestCommittedQC(), null))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void when_sync_to_qc_and_need_sync_but_have_committed__then_should_request_for_qc_sync() {
		Vertex vertex1 = Vertex.createVertex(rootQC, View.of(1), null);
		VertexMetadata vertexMetadata1 = VertexMetadata.ofVertex(vertex1);
		QuorumCertificate qc1 = mock(QuorumCertificate.class);
		when(qc1.getProposed()).thenReturn(vertexMetadata1);

		vertexStore =
			new VertexStore(
				genesisVertex,
				rootQC,
				Collections.singletonList(vertex1),
				syncedStateComputer,
				syncVerticesRPCSender,
				syncSender,
				counters
			);

		Vertex vertex2 = Vertex.createVertex(rootQC, View.of(2), null);
		QuorumCertificate qc2 = mock(QuorumCertificate.class);
		when(qc2.getCommitted()).thenReturn(Optional.of(vertexMetadata1));
		when(qc2.getProposed()).thenReturn(VertexMetadata.ofVertex(vertex2));
		when(qc2.getView()).thenReturn(View.of(2));

		assertThat(vertexStore.syncToQC(qc2, qc2, mock(ECPublicKey.class))).isFalse();

		verify(syncVerticesRPCSender, times(1)).sendGetVerticesRequest(eq(vertex2.getId()), any(), eq(1), any());
	}

	@Test
	public void when_sync_to_qc_and_need_sync_and_committed_qc_is_greater_than_root__then_should_request_for_committed_sync() {
		Vertex vertex1 = Vertex.createVertex(rootQC, View.of(1), null);
		VertexMetadata vertexMetadata1 = VertexMetadata.ofVertex(vertex1);
		QuorumCertificate qc1 = mock(QuorumCertificate.class);
		when(qc1.getProposed()).thenReturn(vertexMetadata1);

		Vertex vertex2 = Vertex.createVertex(rootQC, View.of(2), null);
		QuorumCertificate qc2 = mock(QuorumCertificate.class);
		when(qc2.getCommitted()).thenReturn(Optional.of(vertexMetadata1));
		when(qc2.getProposed()).thenReturn(VertexMetadata.ofVertex(vertex2));
		when(qc2.getView()).thenReturn(View.of(2));

		vertexStore =
			new VertexStore(
				genesisVertex,
				rootQC,
				Collections.singletonList(vertex1),
				syncedStateComputer,
				syncVerticesRPCSender,
				syncSender,
				counters
			);

		Vertex vertex3 = Vertex.createVertex(rootQC, View.of(3), null);
		QuorumCertificate qc3 = mock(QuorumCertificate.class);
		when(qc3.getProposed()).thenReturn(VertexMetadata.ofVertex(vertex3));
		when(qc3.getCommitted()).thenReturn(Optional.of(VertexMetadata.ofVertex(vertex2)));
		when(qc3.getView()).thenReturn(View.of(3));

		assertThat(vertexStore.syncToQC(qc3, qc3, mock(ECPublicKey.class))).isFalse();

		verify(syncVerticesRPCSender, times(1)).sendGetVerticesRequest(eq(vertex3.getId()), any(), eq(3), any());
	}

	@Test
	public void when_sync_to_qc_and_need_sync_but_committed_qc_is_less_than_root__then_should_request_for_qc_sync() {
		Vertex vertex1 = Vertex.createVertex(rootQC, View.of(1), null);
		VertexMetadata vertexMetadata1 = VertexMetadata.ofVertex(vertex1);
		QuorumCertificate qc1 = mock(QuorumCertificate.class);
		when(qc1.getProposed()).thenReturn(vertexMetadata1);

		Vertex vertex2 = Vertex.createVertex(rootQC, View.of(2), null);
		QuorumCertificate qc2 = mock(QuorumCertificate.class);
		when(qc2.getCommitted()).thenReturn(Optional.of(vertexMetadata1));
		when(qc2.getProposed()).thenReturn(VertexMetadata.ofVertex(vertex2));
		when(qc2.getView()).thenReturn(View.of(2));

		vertexStore =
			new VertexStore(
				genesisVertex,
				rootQC,
				Arrays.asList(vertex1, vertex2),
				syncedStateComputer,
				syncVerticesRPCSender,
				syncSender,
				counters
			);

		Vertex vertex3 = Vertex.createVertex(rootQC, View.of(3), null);
		QuorumCertificate qc3 = mock(QuorumCertificate.class);
		when(qc3.getProposed()).thenReturn(VertexMetadata.ofVertex(vertex3));
		when(qc3.getView()).thenReturn(View.of(3));

		assertThat(vertexStore.syncToQC(qc3, rootQC, mock(ECPublicKey.class))).isFalse();

		verify(syncVerticesRPCSender, times(1)).sendGetVerticesRequest(eq(vertex3.getId()), any(), eq(1), any());
	}


	@Test
	public void when_rpc_call_to_get_vertices_with_size_2__then_should_return_both() throws Exception {
		Vertex nextVertex = Vertex.createVertex(rootQC, View.of(1), null);
		vertexStore.insertVertex(nextVertex);
		GetVerticesRequest getVerticesRequest = mock(GetVerticesRequest.class);
		when(getVerticesRequest.getCount()).thenReturn(2);
		when(getVerticesRequest.getVertexId()).thenReturn(nextVertex.getId());
		vertexStore.processGetVerticesRequest(getVerticesRequest);
		verify(syncVerticesRPCSender, times(1)).sendGetVerticesResponse(eq(getVerticesRequest), eq(Arrays.asList(nextVertex, genesisVertex)));
	}
}