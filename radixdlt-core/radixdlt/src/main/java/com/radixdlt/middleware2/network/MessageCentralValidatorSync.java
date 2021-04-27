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

package com.radixdlt.middleware2.network;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.p2p.NodeId;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network.messaging.Message;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Network interface for syncing vertices using the MessageCentral
 */
public class MessageCentralValidatorSync {
	private static final Logger log = LogManager.getLogger();

	private final int magic;
	private final MessageCentral messageCentral;
	private final Hasher hasher;

	@Inject
	public MessageCentralValidatorSync(
		@Named("magic") int magic,
		MessageCentral messageCentral,
		Hasher hasher
	) {
		this.magic = magic;
		this.messageCentral = Objects.requireNonNull(messageCentral);
		this.hasher = Objects.requireNonNull(hasher);
	}

	public RemoteEventDispatcher<GetVerticesRequest> verticesRequestDispatcher() {
		return this::sendGetVerticesRequest;
	}

	public RemoteEventDispatcher<GetVerticesResponse> verticesResponseDispatcher() {
		return this::sendGetVerticesResponse;
	}

	public RemoteEventDispatcher<GetVerticesErrorResponse> verticesErrorResponseDispatcher() {
		return this::sendGetVerticesErrorResponse;
	}

	private void sendGetVerticesRequest(BFTNode node, GetVerticesRequest request) {
		final GetVerticesRequestMessage vertexRequest =
			new GetVerticesRequestMessage(this.magic, request.getVertexId(), request.getCount());
		this.messageCentral.send(NodeId.fromPublicKey(node.getKey()), vertexRequest);
	}

	private void sendGetVerticesResponse(BFTNode node, GetVerticesResponse response) {
		var rawVertices = response.getVertices().stream()
			.map(VerifiedVertex::toSerializable)
			.collect(Collectors.toList());
		var msg = new GetVerticesResponseMessage(this.magic, rawVertices);
		this.messageCentral.send(NodeId.fromPublicKey(node.getKey()), msg);
	}

	public void sendGetVerticesErrorResponse(BFTNode node, GetVerticesErrorResponse response) {
		var request = response.request();
		var requestMsg = new GetVerticesRequestMessage(this.magic, request.getVertexId(), request.getCount());
		var msg = new GetVerticesErrorResponseMessage(this.magic, response.highQC(), requestMsg);
		this.messageCentral.send(NodeId.fromPublicKey(node.getKey()), msg);
	}

	public Flowable<RemoteEvent<GetVerticesRequest>> requests() {
		return this.createFlowable(
			GetVerticesRequestMessage.class,
			(peer, msg) -> {
				final BFTNode node = BFTNode.create(peer.getPublicKey());
				return RemoteEvent.create(node, new GetVerticesRequest(msg.getVertexId(), msg.getCount()));
			}
		);
	}

	public Flowable<RemoteEvent<GetVerticesResponse>> responses() {
		return this.createFlowable(
			GetVerticesResponseMessage.class,
			(src, msg) -> {
				BFTNode node = BFTNode.create(src.getPublicKey());
				// TODO: Move hasher to a more appropriate place
				ImmutableList<VerifiedVertex> hashedVertices = msg.getVertices().stream()
					.map(v -> new VerifiedVertex(v, hasher.hash(v)))
					.collect(ImmutableList.toImmutableList());

				return RemoteEvent.create(node, new GetVerticesResponse(hashedVertices));
			}
		);
	}

	public Flowable<RemoteEvent<GetVerticesErrorResponse>> errorResponses() {
		return this.createFlowable(
			GetVerticesErrorResponseMessage.class,
			(src, msg) -> {
				final var node = BFTNode.create(src.getPublicKey());
				final var request = new GetVerticesRequest(msg.request().getVertexId(), msg.request().getCount());
				return RemoteEvent.create(node, new GetVerticesErrorResponse(msg.highQC(), request));
			}
		);
	}

	private <T extends Message, U> Flowable<U> createFlowable(
		Class<T> c,
		BiFunction<NodeId, T, U> mapper
	) {
		return this.messageCentral.messagesOf(c)
			.toFlowable(BackpressureStrategy.BUFFER)
			.map(m -> mapper.apply(m.getSource(), m.getMessage()));
	}
}
