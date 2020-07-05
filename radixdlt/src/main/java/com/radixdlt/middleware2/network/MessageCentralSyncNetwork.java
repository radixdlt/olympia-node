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
import com.google.inject.name.Named;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.SyncEpochsRPCSender;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.GetVerticesErrorResponse;
import com.radixdlt.consensus.bft.GetVerticesResponse;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.SyncVerticesRPCSender;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.bft.VertexStore.GetVerticesRequest;
import com.radixdlt.crypto.ECPublicKey;
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
import javax.inject.Inject;

/**
 * Network interface for syncing vertices using the MessageCentral
 */
public class MessageCentralSyncNetwork implements SyncVerticesRPCSender, SyncVerticesRPCRx, SyncEpochsRPCSender {

	private final ECPublicKey selfPublicKey;
	private final int magic;
	private final AddressBook addressBook;
	private final MessageCentral messageCentral;
	// TODO: is using a cache in this manner the best way, or should it be managed by the client?
	private final Cache<Hash, Object> opaqueCache = CacheBuilder.newBuilder()
		.expireAfterWrite(30, TimeUnit.SECONDS)
		.build();

	@Inject
	public MessageCentralSyncNetwork(
		@Named("self") ECPublicKey selfPublicKey,
		Universe universe,
		AddressBook addressBook,
		MessageCentral messageCentral
	) {
		this.magic = universe.getMagic();
		this.selfPublicKey = Objects.requireNonNull(selfPublicKey);
		this.addressBook = Objects.requireNonNull(addressBook);
		this.messageCentral = Objects.requireNonNull(messageCentral);
	}

	@Override
	public void sendGetVerticesRequest(Hash id, ECPublicKey node, int count, Object opaque) {
		if (this.selfPublicKey.equals(node)) {
			throw new IllegalStateException("Should never need to retrieve a vertex from self.");
		}

		final Optional<Peer> peer = this.addressBook.peer(node.euid());
		if (!peer.isPresent()) {
			// TODO: Change to more appropriate exception type
			throw new IllegalStateException(String.format("Peer with pubkey %s not present", node));
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
		return Observable.create(emitter -> {
			MessageListener<GetVerticesRequestMessage> listener = (src, msg) -> {
				MessageCentralGetVerticesRequest request = new MessageCentralGetVerticesRequest(src, msg.getVertexId(), msg.getCount());
				emitter.onNext(request);
			};
			this.messageCentral.addListener(GetVerticesRequestMessage.class, listener);
			emitter.setCancellable(() -> this.messageCentral.removeListener(listener));
		});
	}

	@Override
	public Observable<GetVerticesResponse> responses() {
		return Observable.create(emitter -> {
			MessageListener<GetVerticesResponseMessage> listener = (src, msg) -> {
				Object opaque = opaqueCache.getIfPresent(msg.getVertexId());
				if (opaque == null) {
					return; // TODO: send error?
				}

				GetVerticesResponse response = new GetVerticesResponse(msg.getVertexId(), msg.getVertices(), opaque);
				emitter.onNext(response);
			};
			this.messageCentral.addListener(GetVerticesResponseMessage.class, listener);
			emitter.setCancellable(() -> this.messageCentral.removeListener(listener));
		});
	}

	@Override
	public Observable<GetVerticesErrorResponse> errorResponses() {
		return Observable.create(emitter -> {
			MessageListener<GetVerticesErrorResponseMessage> listener = (src, msg) -> {
				Object opaque = opaqueCache.getIfPresent(msg.getVertexId());
				if (opaque == null) {
					return; // TODO: send error?
				}

				GetVerticesErrorResponse response = new GetVerticesErrorResponse(
					msg.getVertexId(),
					msg.getHighestQC(),
					msg.getHighestCommittedQC(),
					opaque
				);
				emitter.onNext(response);
			};
			this.messageCentral.addListener(GetVerticesErrorResponseMessage.class, listener);
			emitter.setCancellable(() -> this.messageCentral.removeListener(listener));
		});
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
	public void sendGetEpochRequest(ECPublicKey node, long epoch) {
		final Optional<Peer> peer = this.addressBook.peer(node.euid());
		if (!peer.isPresent()) {
			// TODO: Change to more appropriate exception type
			throw new IllegalStateException(String.format("Peer with pubkey %s not present", node));
		}

		final GetEpochRequestMessage epochRequest = new GetEpochRequestMessage(this.magic, epoch);
		this.messageCentral.send(peer.get(), epochRequest);
	}

	@Override
	public void sendGetEpochResponse(ECPublicKey node, VertexMetadata ancestor) {
		final Optional<Peer> peer = this.addressBook.peer(node.euid());
		if (!peer.isPresent()) {
			// TODO: Change to more appropriate exception type
			throw new IllegalStateException(String.format("Peer with pubkey %s not present", node));
		}

		final GetEpochResponseMessage epochResponseMessage = new GetEpochResponseMessage(this.magic, ancestor);
		this.messageCentral.send(peer.get(), epochResponseMessage);
	}
}
