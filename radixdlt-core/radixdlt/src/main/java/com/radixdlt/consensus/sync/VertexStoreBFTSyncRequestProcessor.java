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

import com.google.inject.Inject;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessor;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Processor of sync requests and responds with info from a VertexStore
 */
public final class VertexStoreBFTSyncRequestProcessor implements RemoteEventProcessor<GetVerticesRequest> {
	private static final Logger log = LogManager.getLogger();
	private final VertexStore vertexStore;
	private final RemoteEventDispatcher<GetVerticesErrorResponse> errorResponseDispatcher;
	private final RemoteEventDispatcher<GetVerticesResponse> responseDispatcher;

	@Inject
	public VertexStoreBFTSyncRequestProcessor(
		VertexStore vertexStore,
		RemoteEventDispatcher<GetVerticesErrorResponse> errorResponseDispatcher,
		RemoteEventDispatcher<GetVerticesResponse> responseDispatcher
	) {
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.errorResponseDispatcher = Objects.requireNonNull(errorResponseDispatcher);
		this.responseDispatcher = Objects.requireNonNull(responseDispatcher);
	}

	@Override
	public void process(BFTNode sender, GetVerticesRequest request) {
		// TODO: Handle nodes trying to DDOS this endpoint

		log.debug("SYNC_VERTICES: Received GetVerticesRequest {}", request);
		var verticesMaybe = vertexStore.getVertices(request.getVertexId(), request.getCount());
		var singleVerticesMaybe = vertexStore.getVertices(request.getVertexId(), 1);
		log.info("Processing GetVerticesRequest from {}, got vertices? {}, got a single vertex? {} {}",
			sender, verticesMaybe.isPresent(),
			singleVerticesMaybe.isPresent(),
			singleVerticesMaybe);
		verticesMaybe.ifPresentOrElse(
			fetched -> {
				log.debug("SYNC_VERTICES: Sending Response {}", fetched);
				this.responseDispatcher.dispatch(sender, new GetVerticesResponse(fetched));
			},
			() -> {
				log.debug("SYNC_VERTICES: Sending error response {}", vertexStore.highQC());
				this.errorResponseDispatcher.dispatch(sender, new GetVerticesErrorResponse(vertexStore.highQC(), request));
			}
		);
	}
}
