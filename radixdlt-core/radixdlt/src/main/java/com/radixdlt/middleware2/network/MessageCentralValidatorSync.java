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
import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.Inject;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.SyncEpochsRPCRx;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.epoch.GetEpochRequest;
import com.radixdlt.consensus.epoch.GetEpochResponse;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageListener;
import com.radixdlt.universe.Universe;
import io.reactivex.rxjava3.core.Observable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network.messaging.Message;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Network interface for syncing vertices using the MessageCentral
 */
public class MessageCentralValidatorSync implements SyncVerticesResponseSender,
	SyncVerticesRPCRx, SyncEpochsRPCSender, SyncEpochsRPCRx {
	private static final Logger log = LogManager.getLogger();

	private final BFTNode self;
	private final int magic;
	private final AddressBook addressBook;
	private final MessageCentral messageCentral;
	private final Hasher hasher;
	private final SystemCounters counters;
	private final RateLimiter errorResponseRateLimiter;

	@Inject
	public MessageCentralValidatorSync(
		@Self BFTNode self,
		Universe universe,
		AddressBook addressBook,
		MessageCentral messageCentral,
		Hasher hasher,
		SystemCounters counters,
		@GetVerticesErrorRateLimit RateLimiter errorResponseRateLimiter
	) {
		this.magic = universe.getMagic();
		this.self = Objects.requireNonNull(self);
		this.addressBook = Objects.requireNonNull(addressBook);
		this.messageCentral = Objects.requireNonNull(messageCentral);
		this.hasher = Objects.requireNonNull(hasher);
		this.counters = counters;
		this.errorResponseRateLimiter = errorResponseRateLimiter;
	}

	public RemoteEventDispatcher<GetVerticesRequest> verticesRequestDispatcher() {
		return this::sendGetVerticesRequest;
	}

	public void sendGetVerticesRequest(BFTNode node, GetVerticesRequest request) {
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

	@Override
	public void sendGetVerticesResponse(BFTNode node, ImmutableList<VerifiedVertex> vertices) {
		var rawVertices = vertices.stream().map(VerifiedVertex::toSerializable).collect(ImmutableList.toImmutableList());
		GetVerticesResponseMessage response = new GetVerticesResponseMessage(
			this.magic,
			rawVertices
		);

		final Optional<PeerWithSystem> peerMaybe = this.addressBook.peer(node.getKey().euid());
		peerMaybe.ifPresentOrElse(
			p -> this.messageCentral.send(p, response),
			() -> log.warn("{}: Peer {} not in address book when sending GetVerticesResponse", this.self, node)
		);
	}

	@Override
	public void sendGetVerticesErrorResponse(BFTNode node, HighQC highQC) {
		GetVerticesErrorResponseMessage response = new GetVerticesErrorResponseMessage(this.magic, highQC);
		final Optional<PeerWithSystem> peerMaybe = this.addressBook.peer(node.getKey().euid());
		peerMaybe.ifPresentOrElse(
			p -> this.messageCentral.send(p, response),
			() -> log.warn("{}: Peer {} not in address book when sending GetVerticesErrorResponse", this.self, node)
		);
	}

	public Observable<RemoteEvent<GetVerticesRequest>> requests() {
		return this.createObservable(
			GetVerticesRequestMessage.class,
			(peer, msg) -> {
				if (!peer.hasSystem()) {
					return null;
				}

				final BFTNode node = BFTNode.create(peer.getSystem().getKey());
				return RemoteEvent.create(node, new GetVerticesRequest(msg.getVertexId(), msg.getCount()), GetVerticesRequest.class);
			}
		);
	}

	@Override
	public Observable<GetVerticesResponse> responses() {
		return this.createObservable(
			GetVerticesResponseMessage.class,
			(src, msg) -> {
				if (!src.hasSystem()) {
					return null;
				}

				BFTNode node = BFTNode.create(src.getSystem().getKey());
				// TODO: Move hasher to a more appropriate place
				ImmutableList<VerifiedVertex> hashedVertices = msg.getVertices().stream()
					.map(v -> new VerifiedVertex(v, hasher.hash(v)))
					.collect(ImmutableList.toImmutableList());

				return new GetVerticesResponse(node, hashedVertices);
			}
		);
	}

	@Override
	public Observable<GetVerticesErrorResponse> errorResponses() {
		return this.createObservable(
			GetVerticesErrorResponseMessage.class,
			(src, msg) -> {
				if (!src.hasSystem()) {
					return null;
				}

				if (errorResponseRateLimiter.tryAcquire()) {
					BFTNode node = BFTNode.create(src.getSystem().getKey());
					return new GetVerticesErrorResponse(node, msg.highQC());
				} else {
					var counter = counters.increment(CounterType.NETWORKING_DROPPED_ERROR_RESPONSES);

					if (counter == 1 || counter % 1000 == 0) {
						log.warn("Exceeded error response rate, total {} messages dropped", counter);
					}
					return null;
				}
			}
		);
	}

	@Override
	public void sendGetEpochRequest(BFTNode node, long epoch) {
		final GetEpochRequestMessage epochRequest = new GetEpochRequestMessage(this.self, this.magic, epoch);
		final Optional<PeerWithSystem> peerMaybe = this.addressBook.peer(node.getKey().euid());
		peerMaybe.ifPresentOrElse(
			p -> this.messageCentral.send(p, epochRequest),
			() -> log.warn("{}: Peer {} not in address book when sending GetEpochRequest", this.self, node)
		);
	}

	@Override
	public void sendGetEpochResponse(BFTNode node, VerifiedLedgerHeaderAndProof ancestor) {
		final GetEpochResponseMessage epochResponseMessage = new GetEpochResponseMessage(this.self, this.magic, ancestor);
		final Optional<PeerWithSystem> peerMaybe = this.addressBook.peer(node.getKey().euid());
		peerMaybe.ifPresentOrElse(
			p -> this.messageCentral.send(p, epochResponseMessage),
			() -> log.warn("{}: Peer {} not in address book when sending GetEpochResponse", this.self, node)
		);
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
