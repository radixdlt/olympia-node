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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network.messaging.Message;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageFromPeer;
import com.radixdlt.universe.Magic;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.functions.Predicate;

/**
 * Network interface for syncing vertices using the MessageCentral
 */
public class MessageCentralValidatorSync {
	private static final Logger log = LogManager.getLogger();

	private final BFTNode self;
	private final int magic;
	private final AddressBook addressBook;
	private final MessageCentral messageCentral;
	private final Hasher hasher;

	@Inject
	public MessageCentralValidatorSync(
		@Self BFTNode self,
		@Magic int magic,
		AddressBook addressBook,
		MessageCentral messageCentral,
		Hasher hasher
	) {
		this.magic = magic;
		this.self = Objects.requireNonNull(self);
		this.addressBook = Objects.requireNonNull(addressBook);
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
		if (this.self.equals(node)) {
			throw new IllegalStateException("Should never need to retrieve a vertex from self.");
		}

		final Optional<PeerWithSystem> peer = this.addressBook.peer(node.getKey().euid());
		if (!peer.isPresent()) {
			log.warn("{}: Peer {} not in address book when sending GetVerticesRequest", this.self, node);
			return;
		}

		final GetVerticesRequestMessage vertexRequest = new GetVerticesRequestMessage(this.magic, request.getVertexId(), request.getCount());
		this.messageCentral.send(peer.get(), vertexRequest);
	}

	private void sendGetVerticesResponse(BFTNode node, GetVerticesResponse response) {
		var rawVertices = response.getVertices().stream()
			.map(VerifiedVertex::toSerializable).collect(Collectors.toList());
		var msg = new GetVerticesResponseMessage(
			this.magic,
			rawVertices
		);

		final Optional<PeerWithSystem> peerMaybe = this.addressBook.peer(node.getKey().euid());
		peerMaybe.ifPresentOrElse(
			p -> this.messageCentral.send(p, msg),
			() -> log.warn("{}: Peer {} not in address book when sending GetVerticesResponse", this.self, node)
		);
	}

	public void sendGetVerticesErrorResponse(BFTNode node, GetVerticesErrorResponse response) {
		var request = response.request();
		var requestMsg = new GetVerticesRequestMessage(this.magic, request.getVertexId(), request.getCount());
		var msg = new GetVerticesErrorResponseMessage(this.magic, response.highQC(), requestMsg);
		var peerMaybe = this.addressBook.peer(node.getKey().euid());
		peerMaybe.ifPresentOrElse(
			p -> this.messageCentral.send(p, msg),
			() -> log.warn("{}: Peer {} not in address book when sending GetVerticesErrorResponse", this.self, node)
		);
	}

	public Flowable<RemoteEvent<GetVerticesRequest>> requests() {
		return this.createFlowable(
			GetVerticesRequestMessage.class,
			m -> m.getPeer().hasSystem(),
			(peer, msg) -> {
				final BFTNode node = BFTNode.create(peer.getSystem().getKey());
				return RemoteEvent.create(node, new GetVerticesRequest(msg.getVertexId(), msg.getCount()));
			}
		);
	}

	public Flowable<RemoteEvent<GetVerticesResponse>> responses() {
		return this.createFlowable(
			GetVerticesResponseMessage.class,
			m -> m.getPeer().hasSystem(),
			(src, msg) -> {
				BFTNode node = BFTNode.create(src.getSystem().getKey());
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
			m -> m.getPeer().hasSystem(),
			(src, msg) -> {
				final var node = BFTNode.create(src.getSystem().getKey());
				final var request = new GetVerticesRequest(msg.request().getVertexId(), msg.request().getCount());
				return RemoteEvent.create(node, new GetVerticesErrorResponse(msg.highQC(), request));
			}
		);
	}

	private <T extends Message, U> Flowable<U> createFlowable(
		Class<T> c,
		Predicate<MessageFromPeer<?>> filter,
		BiFunction<Peer, T, U> mapper
	) {
		return this.messageCentral.messagesOf(c)
			.toFlowable(BackpressureStrategy.BUFFER)
			.filter(filter)
			.map(m -> mapper.apply(m.getPeer(), m.getMessage()));
	}
}
