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

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.environment.RemoteEventProcessor;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Processor of sync requests and responds with info from a VertexStore
 */
public final class VertexStoreBFTSyncRequestProcessor implements RemoteEventProcessor<GetVerticesRequest> {
	private static final Logger log = LogManager.getLogger();
	private final VertexStore vertexStore;
	private final SyncVerticesResponseSender syncVerticesResponseSender;

	/**
	 * An asynchronous supplier which retrieves data for a vertex with a given id
	 */
	public interface SyncVerticesResponseSender {
		/**
		 * Send a response to a given request
		 * @param node the node to send to
		 * @param vertices the response data of vertices
		 */
		void sendGetVerticesResponse(BFTNode node, ImmutableList<VerifiedVertex> vertices);

		/**
		 * Send an error response to a given request
		 * @param node the node to send to
		 * @param highQC sync info
		 */
		void sendGetVerticesErrorResponse(BFTNode node, HighQC highQC);
	}

	@Inject
	public VertexStoreBFTSyncRequestProcessor(VertexStore vertexStore, SyncVerticesResponseSender syncVerticesResponseSender) {
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.syncVerticesResponseSender = Objects.requireNonNull(syncVerticesResponseSender);
	}

	@Override
	public void process(BFTNode sender, GetVerticesRequest request) {
		// TODO: Handle nodes trying to DDOS this endpoint

		log.debug("SYNC_VERTICES: Received GetVerticesRequest {}", request);
		Optional<ImmutableList<VerifiedVertex>> verticesMaybe = vertexStore.getVertices(request.getVertexId(), request.getCount());
		verticesMaybe.ifPresentOrElse(
			fetched -> {
				log.debug("SYNC_VERTICES: Sending Response {}", fetched);
				this.syncVerticesResponseSender.sendGetVerticesResponse(sender, fetched);
			},
			() -> {
				log.debug("SYNC_VERTICES: Sending error response {}", vertexStore.highQC());
				this.syncVerticesResponseSender.sendGetVerticesErrorResponse(sender, vertexStore.highQC());
			}
		);
	}
}
