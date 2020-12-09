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

package com.radixdlt.consensus.sync;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTSyncer.SyncResult;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.liveness.PacemakerState;
import com.radixdlt.consensus.sync.BFTSync.SyncVerticesRequestSender;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.sync.LocalSyncRequest;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.TypedMocks;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;

public class BFTSyncTest {
	private BFTSync bftSync;
	private VertexStore vertexStore;
	private PacemakerState pacemakerState;
	private Comparator<LedgerHeader> ledgerHeaderComparator;
	private SyncVerticesRequestSender syncVerticesRequestSender;
	private EventDispatcher<LocalSyncRequest> syncLedgerRequestSender;
	private VerifiedLedgerHeaderAndProof verifiedLedgerHeaderAndProof;
	private ScheduledEventDispatcher<LocalGetVerticesRequest> bftSyncTimeoutScheduler;

	@Before
	public void setup() {
		this.vertexStore = mock(VertexStore.class);
		this.ledgerHeaderComparator = rmock(Comparator.class);
		this.pacemakerState = mock(PacemakerState.class);
		this.ledgerHeaderComparator = TypedMocks.rmock(Comparator.class);
		this.syncVerticesRequestSender = mock(SyncVerticesRequestSender.class);
		this.syncLedgerRequestSender = rmock(EventDispatcher.class);
		this.verifiedLedgerHeaderAndProof = mock(VerifiedLedgerHeaderAndProof.class);
		this.bftSyncTimeoutScheduler = rmock(ScheduledEventDispatcher.class);

		bftSync = new BFTSync(
			vertexStore,
			pacemakerState,
			ledgerHeaderComparator,
			syncVerticesRequestSender,
			syncLedgerRequestSender,
			bftSyncTimeoutScheduler,
			verifiedLedgerHeaderAndProof,
			new Random(),
			100
		);
	}


	@Test
	public void given_synced_store__when_sync_to_qc_with_no_author__then_should_return_true() throws Exception {
		VerifiedVertex rootVertex = mock(VerifiedVertex.class);
		when(rootVertex.getView()).thenReturn(View.of(0));
		when(vertexStore.getRoot()).thenReturn(rootVertex);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.of(1));
		BFTHeader header = mock(BFTHeader.class);
		when(header.getView()).thenReturn(View.of(1));
		when(qc.getProposed()).thenReturn(header);
		when(vertexStore.addQC(eq(qc))).thenReturn(true);
		HighQC highQC = mock(HighQC.class);
		when(highQC.highestQC()).thenReturn(qc);
		when(vertexStore.highQC()).thenReturn(highQC);

		assertThat(bftSync.syncToQC(vertexStore.highQC(), null)).isEqualTo(SyncResult.SYNCED);
	}

	@Test
	public void when_sync_to_qc_with_no_author_and_not_synced__then_should_throw_illegal_state_exception() {
		VerifiedVertex rootVertex = mock(VerifiedVertex.class);
		when(rootVertex.getView()).thenReturn(View.of(0));
		when(vertexStore.getRoot()).thenReturn(rootVertex);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getView()).thenReturn(View.of(1));
		BFTHeader header = mock(BFTHeader.class);
		when(header.getView()).thenReturn(View.of(1));
		when(qc.getProposed()).thenReturn(header);
		when(vertexStore.addQC(eq(qc))).thenReturn(false);
		HighQC highQC = mock(HighQC.class);
		when(highQC.highestQC()).thenReturn(qc);
		when(vertexStore.highQC()).thenReturn(highQC);

		assertThatThrownBy(() -> bftSync.syncToQC(vertexStore.highQC(), null))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void when_sync_to_qc_and_less_than_root__then_should_return_invalid() {
		VerifiedVertex rootVertex = mock(VerifiedVertex.class);
		when(rootVertex.getView()).thenReturn(View.of(3));
		when(vertexStore.getRoot()).thenReturn(rootVertex);

		QuorumCertificate qc = mock(QuorumCertificate.class);
		BFTHeader proposed = mock(BFTHeader.class);
		when(proposed.getView()).thenReturn(View.of(2));
		when(qc.getProposed()).thenReturn(proposed);
		HighQC highQC = mock(HighQC.class);
		when(highQC.highestQC()).thenReturn(qc);
		when(highQC.highestCommittedQC()).thenReturn(mock(QuorumCertificate.class));

		SyncResult syncResult = bftSync.syncToQC(highQC, mock(BFTNode.class));

		assertThat(syncResult).isEqualTo(SyncResult.INVALID);
	}

	@Test
	public void when_sync_bad_genesis__then_should_return_invalid() {
		VerifiedVertex rootVertex = mock(VerifiedVertex.class);
		when(rootVertex.getView()).thenReturn(View.of(0));
		when(vertexStore.getRoot()).thenReturn(rootVertex);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		BFTHeader proposed = mock(BFTHeader.class);
		when(proposed.getView()).thenReturn(View.of(0));
		when(qc.getView()).thenReturn(View.of(0));
		when(qc.getProposed()).thenReturn(proposed);
		when(vertexStore.addQC(eq(qc))).thenReturn(false);
		HighQC highQC = mock(HighQC.class);
		when(highQC.highestQC()).thenReturn(qc);
		when(highQC.highestCommittedQC()).thenReturn(mock(QuorumCertificate.class));
		when(vertexStore.highQC()).thenReturn(highQC);

		SyncResult syncResult = bftSync.syncToQC(highQC, mock(BFTNode.class));

		assertThat(syncResult).isEqualTo(SyncResult.INVALID);
	}

	@Test
	public void given_a_sync_which_is_ongoing__when_same_sync_is_attempted__then_in_progress_is_returned() {
		VerifiedVertex rootVertex = mock(VerifiedVertex.class);
		when(rootVertex.getView()).thenReturn(View.of(1));
		when(vertexStore.getRoot()).thenReturn(rootVertex);
		when(vertexStore.addQC(any())).thenReturn(false);
		BFTHeader header = mock(BFTHeader.class);
		HashCode vertexId = mock(HashCode.class);
		when(header.getVertexId()).thenReturn(vertexId);
		when(header.getView()).thenReturn(View.of(2));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getProposed()).thenReturn(header);
		when(qc.getView()).thenReturn(View.of(2));
		QuorumCertificate committedQC = mock(QuorumCertificate.class);
		BFTHeader committedHeader = mock(BFTHeader.class);
		when(committedHeader.getView()).thenReturn(View.of(1));
		when(committedQC.getCommittedAndLedgerStateProof())
			.thenReturn(Optional.of(Pair.of(committedHeader, mock(VerifiedLedgerHeaderAndProof.class))));
		BFTNode author = mock(BFTNode.class);
		HighQC highQC = mock(HighQC.class);
		when(highQC.highestQC()).thenReturn(qc);
		when(highQC.highestCommittedQC()).thenReturn(committedQC);
		bftSync.syncToQC(highQC, author);

		SyncResult syncResult = bftSync.syncToQC(highQC, author);

		assertThat(syncResult).isEqualTo(SyncResult.IN_PROGRESS);
		verify(syncVerticesRequestSender, times(1)).sendGetVerticesRequest(any(), any());
		verify(syncLedgerRequestSender, never()).dispatch(any());
	}

	@Test
	public void when_sync_to_qc_and_need_sync_but_have_committed__then_should_request_for_qc_sync() {
		VerifiedVertex rootVertex = mock(VerifiedVertex.class);
		when(rootVertex.getView()).thenReturn(View.of(1));
		when(vertexStore.getRoot()).thenReturn(rootVertex);
		when(vertexStore.addQC(any())).thenReturn(false);
		BFTHeader header = mock(BFTHeader.class);
		HashCode vertexId = mock(HashCode.class);
		when(header.getVertexId()).thenReturn(vertexId);
		when(header.getView()).thenReturn(View.of(2));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getProposed()).thenReturn(header);
		when(qc.getView()).thenReturn(View.of(2));
		QuorumCertificate committedQC = mock(QuorumCertificate.class);
		BFTHeader committedHeader = mock(BFTHeader.class);
		when(committedHeader.getView()).thenReturn(View.of(1));
		when(committedQC.getCommittedAndLedgerStateProof())
			.thenReturn(Optional.of(Pair.of(committedHeader, mock(VerifiedLedgerHeaderAndProof.class))));
		BFTNode author = mock(BFTNode.class);
		HighQC highQC = mock(HighQC.class);
		when(highQC.highestQC()).thenReturn(qc);
		when(highQC.highestCommittedQC()).thenReturn(committedQC);

		bftSync.syncToQC(highQC, author);

		verify(syncVerticesRequestSender, times(1)).sendGetVerticesRequest(any(), any());
		verify(syncLedgerRequestSender, never()).dispatch(any());
	}

	@Test
	public void when_sync_to_qc_and_need_sync_but_committed_qc_is_less_than_root__then_should_request_for_qc_sync() {
		VerifiedVertex rootVertex = mock(VerifiedVertex.class);
		when(rootVertex.getView()).thenReturn(View.of(2));
		when(vertexStore.getRoot()).thenReturn(rootVertex);
		when(vertexStore.addQC(any())).thenReturn(false);
		BFTHeader header = mock(BFTHeader.class);
		HashCode vertexId = mock(HashCode.class);
		when(header.getVertexId()).thenReturn(vertexId);
		when(header.getView()).thenReturn(View.of(3));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getProposed()).thenReturn(header);
		when(qc.getView()).thenReturn(View.of(3));
		QuorumCertificate committedQC = mock(QuorumCertificate.class);
		BFTHeader committedHeader = mock(BFTHeader.class);
		when(committedHeader.getView()).thenReturn(View.of(1));
		when(committedQC.getCommittedAndLedgerStateProof())
			.thenReturn(Optional.of(Pair.of(committedHeader, mock(VerifiedLedgerHeaderAndProof.class))));
		BFTNode author = mock(BFTNode.class);
		HighQC highQC = mock(HighQC.class);
		when(highQC.highestQC()).thenReturn(qc);
		when(highQC.highestCommittedQC()).thenReturn(committedQC);

		bftSync.syncToQC(highQC, author);

		verify(syncVerticesRequestSender, times(1)).sendGetVerticesRequest(any(), any());
		verify(syncLedgerRequestSender, never()).dispatch(any());
	}

	@Test
	public void when_sync_to_qc_and_need_sync_and_committed_qc_is_greater_than_root__then_should_request_for_committed_sync() {
		VerifiedVertex rootVertex = mock(VerifiedVertex.class);
		when(rootVertex.getView()).thenReturn(View.of(2));
		when(vertexStore.getRoot()).thenReturn(rootVertex);
		when(vertexStore.addQC(any())).thenReturn(false);
		BFTHeader header = mock(BFTHeader.class);
		HashCode vertexId = mock(HashCode.class);
		when(header.getVertexId()).thenReturn(vertexId);
		when(header.getView()).thenReturn(View.of(5));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getProposed()).thenReturn(header);
		when(qc.getView()).thenReturn(View.of(5));
		QuorumCertificate committedQC = mock(QuorumCertificate.class);
		BFTHeader committedHeader = mock(BFTHeader.class);
		when(committedHeader.getView()).thenReturn(View.of(3));
		BFTHeader committedProposed = mock(BFTHeader.class);
		when(committedProposed.getVertexId()).thenReturn(HashUtils.zero256());
		when(committedQC.getProposed()).thenReturn(committedProposed);
		when(committedQC.getCommittedAndLedgerStateProof())
			.thenReturn(Optional.of(Pair.of(committedHeader, mock(VerifiedLedgerHeaderAndProof.class))));
		BFTNode author = mock(BFTNode.class);
		HighQC highQC = mock(HighQC.class);
		when(highQC.highestQC()).thenReturn(qc);
		when(highQC.highestCommittedQC()).thenReturn(committedQC);

		bftSync.syncToQC(highQC, author);

		verify(syncVerticesRequestSender, times(1)).sendGetVerticesRequest(any(), any());
		verify(syncLedgerRequestSender, never()).dispatch(any());
	}


	@Test
	public void given_syncing_to_committed__when_receive_response__then_should_request_for_ledger_sync() {
		VerifiedVertex rootVertex = mock(VerifiedVertex.class);
		when(rootVertex.getView()).thenReturn(View.of(2));
		when(vertexStore.getRoot()).thenReturn(rootVertex);
		when(vertexStore.addQC(any())).thenReturn(false);
		BFTHeader header = mock(BFTHeader.class);
		HashCode vertexId = mock(HashCode.class);
		when(header.getVertexId()).thenReturn(vertexId);
		when(header.getView()).thenReturn(View.of(5));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		when(qc.getProposed()).thenReturn(header);
		when(qc.getView()).thenReturn(View.of(5));
		QuorumCertificate committedQC = mock(QuorumCertificate.class);
		BFTHeader committedHeader = mock(BFTHeader.class);
		BFTHeader committedProposed = mock(BFTHeader.class);
		when(committedQC.getProposed()).thenReturn(committedProposed);
		HashCode committedId = mock(HashCode.class);
		when(committedProposed.getVertexId()).thenReturn(committedId);
		when(committedHeader.getView()).thenReturn(View.of(3));
		VerifiedLedgerHeaderAndProof ledgerHeader = mock(VerifiedLedgerHeaderAndProof.class);
		when(ledgerHeader.getStateVersion()).thenReturn(3L);
		when(committedQC.getCommittedAndLedgerStateProof())
			.thenReturn(Optional.of(Pair.of(committedHeader, ledgerHeader)));
		BFTNode author = mock(BFTNode.class);
		HighQC highQC = mock(HighQC.class);
		when(highQC.highestQC()).thenReturn(qc);
		when(highQC.highestCommittedQC()).thenReturn(committedQC);

		bftSync.syncToQC(highQC, author);

		VerifiedVertex vertex = mock(VerifiedVertex.class);
		when(vertexStore.addQC(any())).thenReturn(true);
		when(vertex.getId()).thenReturn(committedId);
		GetVerticesResponse getVerticesResponse = new GetVerticesResponse(
			mock(BFTNode.class),
			List.of(vertex, vertex, vertex)
		);
		bftSync.responseProcessor().process(getVerticesResponse);

		verify(syncVerticesRequestSender, times(1)).sendGetVerticesRequest(any(), any());
		verify(syncLedgerRequestSender, times(1)).dispatch(any());
	}
}
