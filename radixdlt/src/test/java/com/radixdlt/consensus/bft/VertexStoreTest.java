/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.bft;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.CommittedStateSync;
import com.radixdlt.consensus.Ledger.OnNotSynced;
import com.radixdlt.consensus.Ledger.OnSynced;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.bft.VertexStore.SyncVerticesRPCSender;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.bft.VertexStore.SyncedVertexSender;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.sync.SyncRequestSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.consensus.PreparedCommand;
import com.radixdlt.utils.UInt256;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class VertexStoreTest {
	private Vertex genesisVertex;
	private Supplier<Vertex> nextVertex;
	private Function<Boolean, Vertex> nextSkippableVertex;
	private VertexMetadata genesisVertexMetadata;
	private QuorumCertificate rootQC;
	private VertexStore vertexStore;
	private Ledger ledger;
	private VertexStoreEventSender vertexStoreEventSender;
	private SyncVerticesRPCSender syncVerticesRPCSender;
	private SyncedVertexSender syncedVertexSender;
	private SystemCounters counters;
	private SyncRequestSender syncRequestSender;

	@Before
	public void setUp() {
		ECKeyPair keyPair = ECKeyPair.generateNew();
		BFTValidatorSet bftValidatorSet = BFTValidatorSet.from(ImmutableSet.of(
			BFTValidator.from(BFTNode.create(keyPair.getPublicKey()), UInt256.ONE)
		));
		this.genesisVertex = Vertex.createGenesis(VertexMetadata.ofGenesisAncestor(mock(PreparedCommand.class)));
		this.genesisVertexMetadata = VertexMetadata.ofGenesisVertex(genesisVertex);
		VoteData voteData = new VoteData(genesisVertexMetadata, genesisVertexMetadata, genesisVertexMetadata);
		this.rootQC = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		// No type check issues with mocking generic here
		Ledger ssc = mock(Ledger.class);
		this.ledger = ssc;
		when(this.ledger.prepare(any())).thenReturn(mock(PreparedCommand.class));
		this.syncVerticesRPCSender = mock(SyncVerticesRPCSender.class);
		this.vertexStoreEventSender = mock(VertexStoreEventSender.class);
		this.counters = mock(SystemCounters.class);
		this.syncedVertexSender = mock(SyncedVertexSender.class);
		this.vertexStore = new VertexStore(
			genesisVertex,
			rootQC,
			ledger,
			syncVerticesRPCSender,
			syncedVertexSender,
			vertexStoreEventSender,
			syncRequestSender,
			counters
		);

		AtomicReference<Vertex> lastVertex = new AtomicReference<>(genesisVertex);

		this.nextSkippableVertex = skipOne -> {
			Vertex parentVertex = lastVertex.get();
			final QuorumCertificate qc;
			if (!parentVertex.getView().equals(View.genesis())) {
				VertexMetadata parent = VertexMetadata.ofVertex(parentVertex, mock(PreparedCommand.class));
				VoteData data = new VoteData(parent, parentVertex.getQC().getProposed(), skipOne ? null : parentVertex.getQC().getParent());
				qc = new QuorumCertificate(data, new TimestampedECDSASignatures());
			} else {
				qc = rootQC;
			}

			final View view;
			if (skipOne) {
				view = parentVertex.getView().next().next();
			} else {
				view = parentVertex.getView().next();
			}

			Vertex vertex = Vertex.createVertex(qc, view, null);
			lastVertex.set(vertex);
			return vertex;
		};

		this.nextVertex = () -> nextSkippableVertex.apply(false);
	}

	@Test
	public void when_vertex_store_created_with_incorrect_roots__then_exception_is_thrown() {
		Vertex nextVertex = this.nextVertex.get();
		VertexMetadata nextVertexMetadata = VertexMetadata.ofVertex(nextVertex, mock(PreparedCommand.class));

		VoteData voteData = new VoteData(nextVertexMetadata, genesisVertexMetadata, null);
		QuorumCertificate badRootQC = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		assertThatThrownBy(() -> {
			VertexStore vs = new VertexStore(
				genesisVertex,
				badRootQC, ledger,
				syncVerticesRPCSender,
				syncedVertexSender,
				vertexStoreEventSender,
				syncRequestSender,
				counters
			);
			assertNull(vs); // Fail here
		}).isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void when_vertex_store_created_with_correct_vertices__then_exception_is_not_thrown() {
		Vertex nextVertex = this.nextVertex.get();
		this.vertexStore = new VertexStore(
			genesisVertex,
			rootQC,
			Collections.singletonList(nextVertex),
			ledger,
			syncVerticesRPCSender,
			syncedVertexSender,
			vertexStoreEventSender,
			syncRequestSender,
			counters
		);
	}

	@Test
	public void when_vertex_store_created_with_incorrect_vertices__then_exception_is_thrown() {
		this.nextVertex.get();

		assertThatThrownBy(() ->
			new VertexStore(
				genesisVertex,
				rootQC,
				Collections.singletonList(this.nextVertex.get()),
				ledger,
				syncVerticesRPCSender,
				syncedVertexSender,
				vertexStoreEventSender,
				syncRequestSender,
				counters
			)
		).isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void when_vertex_retriever_succeeds__then_vertex_is_inserted() {
		Vertex vertex = this.nextVertex.get();
		VoteData voteData = new VoteData(VertexMetadata.ofVertex(vertex, mock(PreparedCommand.class)), genesisVertexMetadata, null);
		QuorumCertificate qc = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getId()).thenReturn(vertex.getId());

		BFTNode author = mock(BFTNode.class);

		AtomicReference<Object> opaque = new AtomicReference<>();
		doAnswer(invocation -> {
			opaque.set(invocation.getArgument(3));
			return null;
		}).when(syncVerticesRPCSender).sendGetVerticesRequest(eq(vertex.getId()), any(), eq(1), any());

		assertThat(vertexStore.syncToQC(qc, vertexStore.getHighestCommittedQC(), author)).isFalse();
		verify(syncVerticesRPCSender, times(1)).sendGetVerticesRequest(eq(vertex.getId()), any(), eq(1), any());

		GetVerticesResponse getVerticesResponse = new GetVerticesResponse(vertex.getId(), Collections.singletonList(vertex), opaque.get());
		vertexStore.processGetVerticesResponse(getVerticesResponse);

		verify(syncedVertexSender, times(1)).sendSyncedVertex(eq(vertex));
		assertThat(vertexStore.getHighestQC()).isEqualTo(qc);
	}

	@Test
	@Ignore("Need to catch this at verification of object")
	public void when_inserting_vertex_with_missing_parent__then_missing_parent_exception_is_thrown() {
		/*
		VertexMetadata vertexMetadata = VertexMetadata.ofGenesisAncestor();
		VoteData voteData = new VoteData(vertexMetadata, null, null);
		QuorumCertificate qc = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		Vertex nextVertex = Vertex.createVertex(qc, View.of(1), mock(ClientAtom.class));
		assertThatThrownBy(() -> vertexStore.insertVertex(nextVertex))
			.isInstanceOf(MissingParentException.class);
		 */
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
	public void when_committing_vertex_which_is_lower_than_root__then_empty_optional_is_returned() {
		Vertex vertex1 = nextVertex.get();
		Vertex vertex2 = nextVertex.get();
		Vertex vertex3 = nextVertex.get();
		Vertex vertex4 = nextVertex.get();
		Vertex vertex5 = nextVertex.get();

		vertexStore =
			new VertexStore(
				genesisVertex,
				rootQC,
				Arrays.asList(vertex1, vertex2, vertex3, vertex4, vertex5),
				ledger,
				syncVerticesRPCSender,
				syncedVertexSender,
				vertexStoreEventSender,
				syncRequestSender,
				counters
			);

		VertexMetadata vertexMetadata2 = VertexMetadata.ofVertex(vertex2, mock(PreparedCommand.class));
		vertexStore.commitVertex(vertexMetadata2);
		assertThat(vertexStore.commitVertex(vertexMetadata2)).isPresent();

		VertexMetadata vertexMetadata1 = VertexMetadata.ofVertex(vertex1, mock(PreparedCommand.class));
		assertThat(vertexStore.commitVertex(vertexMetadata1)).isNotPresent();
	}

	@Test
	public void when_insert_vertex__then_it_should_not_be_committed_or_stored_in_engine()
		throws VertexInsertionException {
		Vertex nextVertex = Vertex.createVertex(rootQC, View.of(1), null);
		vertexStore.insertVertex(nextVertex);

		verify(vertexStoreEventSender, never()).sendCommittedVertex(any());
		verify(ledger, times(0)).commit(any(), any()); // not stored
	}

	@Test
	public void when_insert_and_commit_vertex__then_it_should_be_committed_and_stored_in_engine()
		throws VertexInsertionException {
		Command command = mock(Command.class);
		Vertex nextVertex = Vertex.createVertex(rootQC, View.of(1), command);
		vertexStore.insertVertex(nextVertex);

		VertexMetadata vertexMetadata = VertexMetadata.ofVertex(nextVertex, mock(PreparedCommand.class));
		assertThat(vertexStore.commitVertex(vertexMetadata)).hasValue(nextVertex);

		verify(vertexStoreEventSender, times(1))
			.sendCommittedVertex(eq(nextVertex));
		verify(ledger, times(1))
			.commit(eq(command), eq(vertexMetadata)); // next atom stored
	}

	@Test
	public void when_insert_two_vertices__then_get_path_from_root_should_return_the_two_vertices() throws Exception {
		Vertex nextVertex0 = nextVertex.get();
		Vertex nextVertex1 = nextVertex.get();
		vertexStore.insertVertex(nextVertex0);
		vertexStore.insertVertex(nextVertex1);
		assertThat(vertexStore.getPathFromRoot(nextVertex1.getId()))
			.isEqualTo(Arrays.asList(nextVertex1, nextVertex0));
	}

	@Test
	public void when_insert_and_commit_vertex__then_committed_vertex_should_emit_and_store_should_have_size_1()
		throws Exception {
		Vertex vertex = nextVertex.get();
		vertexStore.insertVertex(vertex);

		VertexMetadata vertexMetadata = VertexMetadata.ofVertex(vertex, mock(PreparedCommand.class));
		assertThat(vertexStore.commitVertex(vertexMetadata)).hasValue(vertex);
		verify(vertexStoreEventSender, times(1)).sendCommittedVertex(eq(vertex));
		assertThat(vertexStore.getSize()).isEqualTo(1);
	}

	@Test
	public void when_insert_and_commit_vertex_2x__then_committed_vertex_should_emit_in_order_and_store_should_have_size_1()
		throws Exception {

		Vertex nextVertex1 = nextVertex.get();
		vertexStore.insertVertex(nextVertex1);
		VertexMetadata vertexMetadata = VertexMetadata.ofVertex(nextVertex1, mock(PreparedCommand.class));
		vertexStore.commitVertex(vertexMetadata);

		Vertex nextVertex2 = nextVertex.get();
		vertexStore.insertVertex(nextVertex2);
		VertexMetadata vertexMetadata2 = VertexMetadata.ofVertex(nextVertex2, mock(PreparedCommand.class));
		vertexStore.commitVertex(vertexMetadata2);

		verify(vertexStoreEventSender, times(1)).sendCommittedVertex(eq(nextVertex1));
		verify(vertexStoreEventSender, times(1)).sendCommittedVertex(eq(nextVertex2));
		assertThat(vertexStore.getSize()).isEqualTo(1);
		assertThat(vertexStore.getSize()).isEqualTo(1);
	}

	@Test
	public void when_insert_two_and_commit_vertex__then_two_committed_vertices_should_emit_in_order_and_store_should_have_size_1()
		throws Exception {
		Vertex nextVertex1 = nextVertex.get();
		vertexStore.insertVertex(nextVertex1);

		QuorumCertificate qc = mock(QuorumCertificate.class);
		VertexMetadata vertexMetadata1 = mock(VertexMetadata.class);
		when(vertexMetadata1.getId()).thenReturn(nextVertex1.getId());
		when(vertexMetadata1.getView()).thenReturn(nextVertex1.getView());
		when(qc.getProposed()).thenReturn(vertexMetadata1);

		Vertex nextVertex2 = nextVertex.get();
		vertexStore.insertVertex(nextVertex2);
		VertexMetadata vertexMetadata2 = mock(VertexMetadata.class);
		when(vertexMetadata2.getId()).thenReturn(nextVertex2.getId());
		when(vertexMetadata2.getView()).thenReturn(nextVertex2.getView());

		vertexStore.commitVertex(vertexMetadata2);
		verify(vertexStoreEventSender, times(1)).sendCommittedVertex(eq(nextVertex1));
		verify(vertexStoreEventSender, times(1)).sendCommittedVertex(eq(nextVertex2));
		assertThat(vertexStore.getSize()).isEqualTo(1);
	}

	@Test
	public void when_sync_to_qc_which_doesnt_exist_and_vertex_is_inserted_later__then_sync_should_be_emitted() throws Exception {
		Vertex vertex = nextVertex.get();
		QuorumCertificate qc = mock(QuorumCertificate.class);

		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getId()).thenReturn(vertex.getId());
		when(vertexMetadata.getView()).thenReturn(vertex.getView());
		when(qc.getProposed()).thenReturn(vertexMetadata);

		assertThat(vertexStore.syncToQC(qc, vertexStore.getHighestCommittedQC(), mock(BFTNode.class))).isFalse();
		vertexStore.insertVertex(vertex);
		verify(syncedVertexSender, times(1)).sendSyncedVertex(eq(vertex));
	}

	@Test
	public void when_sync_to_qc_with_no_author_and_synced__then_should_return_true() throws Exception {
		Vertex vertex = nextVertex.get();
		vertexStore.insertVertex(vertex);

		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.of(1));

		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getId()).thenReturn(vertex.getId());
		when(vertexMetadata.getView()).thenReturn(vertex.getView());

		when(qc.getProposed()).thenReturn(vertexMetadata);

		assertThat(vertexStore.syncToQC(qc, vertexStore.getHighestCommittedQC(), null)).isTrue();
	}

	@Test
	public void when_sync_to_qc_with_no_author_and_not_synced__then_should_throw_illegal_state_exception() {
		Vertex vertex = nextVertex.get();
		QuorumCertificate qc = mock(QuorumCertificate.class);

		VertexMetadata vertexMetadata = mock(VertexMetadata.class);
		when(vertexMetadata.getId()).thenReturn(vertex.getId());
		when(vertexMetadata.getView()).thenReturn(vertex.getView());
		when(qc.getProposed()).thenReturn(vertexMetadata);

		assertThatThrownBy(() -> vertexStore.syncToQC(qc, vertexStore.getHighestCommittedQC(), null))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void when_sync_to_qc_and_need_sync_but_have_committed__then_should_request_for_qc_sync() {
		Vertex vertex1 = nextVertex.get();
		Vertex vertex2 = nextVertex.get();

		vertexStore =
			new VertexStore(
				genesisVertex,
				rootQC,
				Arrays.asList(vertex1, vertex2),
				ledger,
				syncVerticesRPCSender,
				syncedVertexSender,
				vertexStoreEventSender,
				syncRequestSender,
				counters
			);

		Vertex vertex3 = nextVertex.get();
		Vertex vertex4 = nextVertex.get();
		assertThat(vertexStore.syncToQC(vertex4.getQC(), vertex4.getQC(), mock(BFTNode.class))).isFalse();

		verify(syncVerticesRPCSender, times(1)).sendGetVerticesRequest(eq(vertex3.getId()), any(), eq(1), any());
	}

	@Test
	public void when_sync_to_qc_and_need_sync_but_committed_qc_is_less_than_root__then_should_request_for_qc_sync() {
		Vertex vertex1 = nextVertex.get();
		Vertex vertex2 = nextVertex.get();
		Vertex vertex3 = nextVertex.get();
		Vertex vertex4 = nextVertex.get();


		vertexStore =
			new VertexStore(
				genesisVertex,
				rootQC,
				Arrays.asList(vertex1, vertex2, vertex3, vertex4),
				ledger,
				syncVerticesRPCSender,
				syncedVertexSender,
				vertexStoreEventSender,
				syncRequestSender,
				counters
			);

		Vertex vertex5 = nextVertex.get();
		Vertex vertex6 = nextVertex.get();

		assertThat(vertexStore.syncToQC(vertex6.getQC(), rootQC, mock(BFTNode.class))).isFalse();

		verify(syncVerticesRPCSender, times(1)).sendGetVerticesRequest(eq(vertex5.getId()), any(), eq(1), any());
	}

	@Test
	public void when_sync_to_qc_and_need_sync_and_committed_qc_is_greater_than_root__then_should_request_for_committed_sync() {
		Vertex vertex1 = nextVertex.get();
		Vertex vertex2 = nextVertex.get();
		Vertex vertex3 = nextVertex.get();
		Vertex vertex4 = nextVertex.get();

		vertexStore =
			new VertexStore(
				genesisVertex,
				rootQC,
				Arrays.asList(vertex1, vertex2, vertex3, vertex4),
				ledger,
				syncVerticesRPCSender,
				syncedVertexSender,
				vertexStoreEventSender,
				syncRequestSender,
				counters
			);

		// Skip two vertices
		nextVertex.get();
		nextVertex.get();

		Vertex vertex7 = nextVertex.get();
		Vertex vertex8 = nextVertex.get();
		Vertex vertex9 = nextSkippableVertex.apply(true);

		assertThat(vertexStore.syncToQC(vertex9.getQC(), vertex8.getQC(), mock(BFTNode.class))).isFalse();

		verify(syncVerticesRPCSender, times(1)).sendGetVerticesRequest(eq(vertex7.getId()), any(), eq(3), any());
	}

	@Test
	public void when_request_for_qc_sync_and_receive_response__then_should_update() {
		Vertex vertex1 = nextVertex.get();
		Vertex vertex2 = nextVertex.get();

		vertexStore =
			new VertexStore(
				genesisVertex,
				rootQC,
				Arrays.asList(vertex1, vertex2),
				ledger,
				syncVerticesRPCSender,
				syncedVertexSender,
				vertexStoreEventSender,
				syncRequestSender,
				counters
			);

		Vertex vertex3 = nextVertex.get();
		Vertex vertex4 = nextVertex.get();
		Vertex vertex5 = nextVertex.get();

		AtomicReference<Object> opaque = new AtomicReference<>();
		doAnswer(invocation -> {
			opaque.set(invocation.getArgument(3));
			return null;
		}).when(syncVerticesRPCSender).sendGetVerticesRequest(any(), any(), eq(1), any());

		assertThat(vertexStore.syncToQC(vertex5.getQC(), vertex5.getQC(), mock(BFTNode.class))).isFalse();

		verify(syncVerticesRPCSender, times(1)).sendGetVerticesRequest(eq(vertex4.getId()), any(), eq(1), any());
		GetVerticesResponse response1 = new GetVerticesResponse(vertex4.getId(), Collections.singletonList(vertex4), opaque.get());
		vertexStore.processGetVerticesResponse(response1);

		verify(syncVerticesRPCSender, times(1)).sendGetVerticesRequest(eq(vertex3.getId()), any(), eq(1), any());
		GetVerticesResponse response2 = new GetVerticesResponse(vertex3.getId(), Collections.singletonList(vertex3), opaque.get());
		vertexStore.processGetVerticesResponse(response2);


		assertThat(vertexStore.getHighestQC()).isEqualTo(vertex5.getQC());
		verify(syncedVertexSender, times(1)).sendSyncedVertex(eq(vertex4));
	}

	@Test
	public void when_request_for_committed_sync_and_receive_response__then_should_update() {
		Vertex vertex1 = nextVertex.get();
		Vertex vertex2 = nextVertex.get();
		Vertex vertex3 = nextVertex.get();
		Vertex vertex4 = nextVertex.get();

		vertexStore =
			new VertexStore(
				genesisVertex,
				rootQC,
				Arrays.asList(vertex1, vertex2, vertex3, vertex4),
				ledger,
				syncVerticesRPCSender,
				syncedVertexSender,
				vertexStoreEventSender,
				syncRequestSender,
				counters
			);

		Vertex vertex5 = nextVertex.get();
		Vertex vertex6 = nextVertex.get();
		Vertex vertex7 = nextVertex.get();
		Vertex vertex8 = nextVertex.get();

		AtomicReference<Object> rpcOpaque = new AtomicReference<>();
		doAnswer(invocation -> {
			rpcOpaque.set(invocation.getArgument(3));
			return null;
		}).when(syncVerticesRPCSender).sendGetVerticesRequest(eq(vertex7.getId()), any(), eq(3), any());

		AtomicReference<Object> stateOpaque = new AtomicReference<>();
		AtomicReference<VertexMetadata> vertexMetadataAtomicReference = new AtomicReference<>();

		OnSynced onSynced = mock(OnSynced.class);
		OnNotSynced onNotSynced = mock(OnNotSynced.class);

		when(onSynced.then(any())).thenReturn(onNotSynced);
		doAnswer(invocation -> {
			stateOpaque.set(invocation.getArgument(1));
			return false;
		}).when(onNotSynced).elseExecuteAndSendMessageOnSync(any(), any());

		doAnswer(invocation -> {
			vertexMetadataAtomicReference.set(invocation.getArgument(0));
			return onSynced;
		}).when(ledger).ifCommitSynced(any());

		vertexStore.syncToQC(vertex8.getQC(), vertex8.getQC(), mock(BFTNode.class));
		GetVerticesResponse response = new GetVerticesResponse(vertex7.getId(), Arrays.asList(vertex7, vertex6, vertex5), rpcOpaque.get());
		vertexStore.processGetVerticesResponse(response);
		assertThat(vertexStore.getHighestQC()).isEqualTo(vertex4.getQC());
		assertThat(vertexStore.getHighestCommittedQC()).isEqualTo(vertex4.getQC());

		CommittedStateSync committedStateSync = new CommittedStateSync(
			vertexMetadataAtomicReference.get().getPreparedCommand().getStateVersion(), stateOpaque.get()
		);
		vertexStore.processCommittedStateSync(committedStateSync);

		assertThat(vertexStore.getHighestQC()).isEqualTo(vertex8.getQC());
		assertThat(vertexStore.getHighestCommittedQC()).isEqualTo(vertex8.getQC());
	}

	@Test
	public void when_rpc_call_to_get_vertices_with_size_2__then_should_return_both() throws Exception {
		Vertex vertex = nextVertex.get();
		vertexStore.insertVertex(vertex);
		GetVerticesRequest getVerticesRequest = mock(GetVerticesRequest.class);
		when(getVerticesRequest.getCount()).thenReturn(2);
		when(getVerticesRequest.getVertexId()).thenReturn(vertex.getId());
		vertexStore.processGetVerticesRequest(getVerticesRequest);
		verify(syncVerticesRPCSender, times(1)).sendGetVerticesResponse(eq(getVerticesRequest), eq(ImmutableList.of(vertex, genesisVertex)));
	}
}