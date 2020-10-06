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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.SyncInfo;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class VertexStoreBFTSyncRequestProcessorTest {
	private VertexStoreBFTSyncRequestProcessor requestProcessor;
	private VertexStore vertexStore;
	private SyncVerticesResponseSender responseSender;

	@Before
	public void setup() {
		this.vertexStore = mock(VertexStore.class);
		this.responseSender = mock(SyncVerticesResponseSender.class);
		this.requestProcessor = new VertexStoreBFTSyncRequestProcessor(vertexStore, responseSender);
	}

	@Test
	public void given_vertex_store_which_doesnt_contain_vertices__when_request__then_should_send_error() {
		when(vertexStore.getVertices(any(), anyInt())).thenReturn(Optional.empty());
		QuorumCertificate qc = mock(QuorumCertificate.class);
		QuorumCertificate committedQC = mock(QuorumCertificate.class);
		SyncInfo syncInfo = mock(SyncInfo.class);
		when(syncInfo.highestQC()).thenReturn(qc);
		when(syncInfo.highestCommittedQC()).thenReturn(committedQC);
		when(vertexStore.syncInfo()).thenReturn(syncInfo);

		GetVerticesRequest request = mock(GetVerticesRequest.class);
		BFTNode sender = mock(BFTNode.class);
		when(request.getSender()).thenReturn(sender);
		requestProcessor.processGetVerticesRequest(request);

		verify(responseSender, times(1)).sendGetVerticesErrorResponse(eq(sender), eq(syncInfo));
		verify(responseSender, never()).sendGetVerticesResponse(any(), any());
	}


	@Test
	public void given_vertex_store_which_does_contain_vertices__when_request__then_should_send_vertices() {
		when(vertexStore.getVertices(any(), anyInt())).thenReturn(Optional.of(ImmutableList.of()));
		QuorumCertificate qc = mock(QuorumCertificate.class);
		QuorumCertificate committedQC = mock(QuorumCertificate.class);
		SyncInfo syncInfo = mock(SyncInfo.class);
		when(syncInfo.highestQC()).thenReturn(qc);
		when(syncInfo.highestCommittedQC()).thenReturn(committedQC);
		when(vertexStore.syncInfo()).thenReturn(syncInfo);

		GetVerticesRequest request = mock(GetVerticesRequest.class);
		BFTNode sender = mock(BFTNode.class);
		when(request.getSender()).thenReturn(sender);
		requestProcessor.processGetVerticesRequest(request);

		verify(responseSender, never()).sendGetVerticesErrorResponse(any(), any());
		verify(responseSender, times(1)).sendGetVerticesResponse(eq(sender), any());
	}
}