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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.SyncEpochsRPCRx;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.GetVerticesErrorResponse;
import com.radixdlt.consensus.bft.GetVerticesResponse;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.bft.VertexStore.SyncVerticesRPCSender;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.bft.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.epoch.GetEpochRequest;
import com.radixdlt.consensus.epoch.GetEpochResponse;
import com.radixdlt.crypto.Hash;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageListener;
import com.radixdlt.universe.Universe;
import io.reactivex.rxjava3.core.Observable;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network.messaging.Message;

/**
 * Network interface for syncing vertices using the MessageCentral
 */
public class MessageCentralValidatorSync implements SyncVerticesRPCSender, SyncVerticesRPCRx, SyncEpochsRPCSender, SyncEpochsRPCRx {
	private static final Logger log = LogManager.getLogger();

	private final BFTNode self;
	private final int magic;
	private final AddressBook addressBook;
	private final MessageCentral messageCentral;
	// TODO: is using a cache in this manner the best way, or should it be managed by the client?
	private final Cache<Hash, Object> opaqueCache = CacheBuilder.newBuilder()
		.expireAfterWrite(30, TimeUnit.SECONDS)
		.build();

	public MessageCentralValidatorSync(
		BFTNode self,
		Universe universe,
		AddressBook addressBook,
		MessageCentral messageCentral
	) {
		this.magic = universe.getMagic();
		this.self = Objects.requireNonNull(self);
		this.addressBook = Objects.requireNonNull(addressBook);
		this.messageCentral = Objects.requireNonNull(messageCentral);
	}

	@Override
	public void sendGetVerticesRequest(Hash id, BFTNode node, int count, Object opaque) {
		if (this.self.equals(node)) {
			throw new IllegalStateException("Should never need to retrieve a vertex from self.");
		}

		final Optional<Peer> peer = this.addressBook.peer(node.getKey().euid());
		if (!peer.isPresent()) {
			log.warn("{}: Peer {} not in address book when sending GetVerticesRequest", this.self, node);
			return;
		}

		opaqueCache.put(id, opaque);

		final GetVerticesRequestMessage vertexRequest = new GetVerticesRequestMessage(this.magic, id, count);
		this.messageCentral.send(peer.get(), vertexRequest);
	}

	@Override
	public void sendGetVerticesResponse(GetVerticesRequest originalRequest, ImmutableList<Vertex> vertices) {
		MessageCentralGetVerticesRequest messageCentralGetVerticesRequest = (MessageCentralGetVerticesRequest) originalRequest;
		GetVerticesResponseMessage response = new GetVerticesResponseMessage(this.magic, messageCentralGetVerticesRequest.getVertexId(), vertices);
		Peer peer = messageCentralGetVerticesRequest.getRequestor();
		this.messageCentral.send(peer, response);
	}

	@Override
	public void sendGetVerticesErrorResponse(GetVerticesRequest originalRequest, QuorumCertificate highestQC, QuorumCertificate highestCommittedQC) {
		MessageCentralGetVerticesRequest messageCentralGetVerticesRequest = (MessageCentralGetVerticesRequest) originalRequest;
		GetVerticesErrorResponseMessage response = new GetVerticesErrorResponseMessage(
			this.magic,
			messageCentralGetVerticesRequest.getVertexId(),
			highestQC,
			highestCommittedQC
		);
		Peer peer = messageCentralGetVerticesRequest.getRequestor();
		this.messageCentral.send(peer, response);
	}

	@Override
	public Observable<GetVerticesRequest> requests() {
		return this.createObservable(
			GetVerticesRequestMessage.class,
			(peer, msg) -> new MessageCentralGetVerticesRequest(peer, msg.getVertexId(), msg.getCount())
		);
	}

	@Override
	public Observable<GetVerticesResponse> responses() {
		return this.createObservable(
			GetVerticesResponseMessage.class,
			(peer, msg) -> {
				Object opaque = opaqueCache.getIfPresent(msg.getVertexId());
				if (opaque == null) {
					return null; // TODO: send error?
				}

				return new GetVerticesResponse(msg.getVertexId(), msg.getVertices(), opaque);
			}
		);
	}

	@Override
	public Observable<GetVerticesErrorResponse> errorResponses() {
		return this.createObservable(
			GetVerticesErrorResponseMessage.class,
			(peer, msg) -> {
				Object opaque = opaqueCache.getIfPresent(msg.getVertexId());
				if (opaque == null) {
					return null; // TODO: send error?
				}

				return new GetVerticesErrorResponse(
					msg.getVertexId(),
					msg.getHighestQC(),
					msg.getHighestCommittedQC(),
					opaque
				);
			}
		);
	}

	/**
	 * An RPC request to retrieve a given vertex
	 */
	static final class MessageCentralGetVerticesRequest implements GetVerticesRequest {
		private final Peer requestor;
		private final Hash vertexId;
		private final int count;

		MessageCentralGetVerticesRequest(Peer requestor, Hash vertexId, int count) {
			this.requestor = requestor;
			this.vertexId = vertexId;
			this.count = count;
		}

		Peer getRequestor() {
			return requestor;
		}

		@Override
		public Hash getVertexId() {
			return vertexId;
		}

		@Override
		public int getCount() {
			return count;
		}

		@Override
		public String toString() {
			return String.format("%s{vertexId=%s count=%d}", getClass().getSimpleName(), vertexId.toString().substring(0, 6), count);
		}
	}

	@Override
	public void sendGetEpochRequest(BFTNode node, long epoch) {
		final Optional<Peer> peer = this.addressBook.peer(node.getKey().euid());
		if (!peer.isPresent()) {
			log.warn("{}: Peer {} not in address book when sending GetEpochRequest", this.self, node);
			return;
		}

		final GetEpochRequestMessage epochRequest = new GetEpochRequestMessage(this.self, this.magic, epoch);
		this.messageCentral.send(peer.get(), epochRequest);
	}

	@Override
	public void sendGetEpochResponse(BFTNode node, VertexMetadata ancestor) {
		final Optional<Peer> peer = this.addressBook.peer(node.getKey().euid());
		if (!peer.isPresent()) {
			log.warn("{}: Peer {} not in address book when sending GetEpochResponse", this.self, node);
			return;
		}

		final GetEpochResponseMessage epochResponseMessage = new GetEpochResponseMessage(this.self, this.magic, ancestor);
		this.messageCentral.send(peer.get(), epochResponseMessage);
	}

	@Override
	public Observable<GetEpochRequest> epochRequests() {
		return this.createObservable(
			GetEpochRequestMessage.class,
			(peer, msg) -> new GetEpochRequest(msg.getAuthor(), msg.getEpoch())
		);
	}

	@Override
	public Observable<GetEpochResponse> epochResponses() {
		return this.createObservable(
			GetEpochResponseMessage.class,
			(peer, msg) -> new GetEpochResponse(msg.getAuthor(), msg.getAncestor())
		);
	}

	private <T extends Message, U> Observable<U> createObservable(Class<T> c, BiFunction<Peer, T, U> mapper) {
		return Observable.create(emitter -> {
			MessageListener<T> listener = (src, msg) -> {
				U u = mapper.apply(src, msg);
				if (u != null) {
					emitter.onNext(u);
				}
			};
			this.messageCentral.addListener(c, listener);
			emitter.setCancellable(() -> this.messageCentral.removeListener(listener));
		});
	}
}
