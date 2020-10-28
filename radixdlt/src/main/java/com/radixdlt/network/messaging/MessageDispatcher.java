/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.network.messaging;

import com.radixdlt.consensus.HashSigner;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.Hasher;
import org.radix.Radix;

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.transport.SendResult;
import com.radixdlt.network.transport.Transport;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.TransportOutboundConnection;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.DsonOutput.Output;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network.messaging.Message;
import org.radix.network.messaging.SignedMessage;
import org.radix.time.Timestamps;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.RadixSystem;
import org.radix.universe.system.SystemMessage;
import org.xerial.snappy.Snappy;

/*
 * This could be moved into MessageCentralImpl at some stage, but has been
 * separated out so that we can check if all the functionality here is
 * required, and remove the stuff we don't want to keep.
 */
class MessageDispatcher {
	private static final Logger log = LogManager.getLogger();

	private final long messageTtlMs;
	private final SystemCounters counters;
	private final Serialization serialization;
	private final TimeSupplier timeSource;
	private final LocalSystem localSystem;
	private final AddressBook addressBook;
	private final Hasher hasher;
	private final HashSigner hashSigner;

	MessageDispatcher(
		SystemCounters counters,
		MessageCentralConfiguration config,
		Serialization serialization,
		TimeSupplier timeSource,
		LocalSystem localSystem,
		AddressBook addressBook,
		Hasher hasher,
		HashSigner hashSigner
	) {
		this.messageTtlMs = config.messagingTimeToLive(30_000L);
		this.counters = counters;
		this.serialization = serialization;
		this.timeSource = timeSource;
		this.localSystem = localSystem;
		this.addressBook = addressBook;
		this.hasher = hasher;
		this.hashSigner = hashSigner;
	}

	CompletableFuture<SendResult> send(TransportManager transportManager, final OutboundMessageEvent outboundMessage) {
		final Message message = outboundMessage.message();
		final Peer peer = outboundMessage.peer();

		if (timeSource.currentTime() - message.getTimestamp() > messageTtlMs) {
			String msg = String.format("TTL for %s message to %s has expired", message.getClass().getSimpleName(), peer);
			log.warn(msg);
			this.counters.increment(CounterType.MESSAGES_OUTBOUND_ABORTED);
			return CompletableFuture.completedFuture(SendResult.failure(new IOException(msg)));
		}

		if (message instanceof SignedMessage) {
			SignedMessage signedMessage = (SignedMessage) message;
			if (signedMessage.getSignature() == null) {
				byte[] hash = hasher.hash(signedMessage).asBytes();
				signedMessage.setSignature(hashSigner.sign(hash));
			}
		}

		byte[] bytes = serialize(message);
		return findTransportAndOpenConnection(transportManager, peer, bytes)
			.thenCompose(conn -> send(conn, message, bytes))
			.thenApply(this::updateStatistics)
			.exceptionally(t -> completionException(t, peer, message));
	}

	private CompletableFuture<SendResult> send(TransportOutboundConnection conn, Message message, byte[] bytes) {
		log.trace("Sending to {}: {}", conn, message);
		this.counters.add(CounterType.NETWORKING_SENT_BYTES, bytes.length);
		return conn.send(bytes);
	}

	private SendResult completionException(Throwable cause, Peer receiver, Message message) {
		String msg = String.format("Send %s to %s failed", message.getClass().getSimpleName(), receiver);
		log.warn(msg, cause);
		return SendResult.failure(new IOException(msg, cause));
	}

	void receive(MessageListenerList listeners, final InboundMessageEvent inboundMessage) {
		final TransportInfo source = inboundMessage.source();
		final Message message = inboundMessage.message();

		Optional<PeerWithSystem> peer = this.addressBook.peer(source);

		long currentTime = timeSource.currentTime();
		peer.ifPresent(p -> p.setTimestamp(Timestamps.ACTIVE, currentTime));
		this.counters.increment(CounterType.MESSAGES_INBOUND_RECEIVED);

		boolean isBanned = peer.map(Peer::isBanned).orElse(false);
		if (isBanned || currentTime - message.getTimestamp() > messageTtlMs) {
			this.counters.increment(CounterType.MESSAGES_INBOUND_DISCARDED);
			return;
		}

		try {
			if (message instanceof SystemMessage) {
				peer = handleSystemMessage(peer, source, (SystemMessage) message);
				if (!peer.isPresent()) {
					return;
				}
			} else if (message instanceof SignedMessage && !handleSignedMessage(peer, (SignedMessage) message)) {
				return;
			}
		} catch (Exception ex) {
			String msg = String.format("%s: Pre-processing from %s failed", message.getClass().getSimpleName(), source);
			log.error(msg, ex);
			return;
		}

		if (log.isTraceEnabled()) {
			log.trace("Received from {}: {}", hostId(peer), message);
		}
		peer.ifPresent(p -> {
			listeners.messageReceived(p, message);
			this.counters.increment(CounterType.MESSAGES_INBOUND_PROCESSED);
		});
	}

	private Optional<PeerWithSystem> handleSystemMessage(
		Optional<PeerWithSystem> oldPeer,
		TransportInfo source,
		SystemMessage systemMessage
	) {
		String messageType = systemMessage.getClass().getSimpleName();
		RadixSystem system = systemMessage.getSystem();
		if (checkSignature(systemMessage, system)) {
			PeerWithSystem peer = this.addressBook.updatePeerSystem(oldPeer, system, source);
			log.trace("Good signature on {} from {}", messageType, peer);
			if (system.getNID() == null || EUID.ZERO.equals(system.getNID())) {
				peer.ban(String.format("%s:%s gave null NID", peer, messageType));
				return Optional.empty();
			}
			if (systemMessage.getSystem().getAgentVersion() <= Radix.REFUSE_AGENT_VERSION) {
				peer.ban(String.format("Old peer %s %s:%s", peer, system.getAgent(), system.getProtocolVersion()));
				return Optional.empty();
			}
			if (system.getNID().equals(this.localSystem.getNID())) {
				// Just quietly ignore messages from self
				log.trace("Ignoring {} message from self", messageType);
				return Optional.empty();
			}
			if (checkPeerBanned(system.getNID(), messageType)) {
				return Optional.empty();
			}
			return Optional.of(peer);
		}
		log.warn("Ignoring {} message from {} - bad signature", messageType, oldPeer);
		this.counters.increment(CounterType.MESSAGES_INBOUND_BADSIGNATURE);
		return Optional.empty();
	}

	private boolean handleSignedMessage(Optional<PeerWithSystem> peer, SignedMessage signedMessage) {
		String messageType = signedMessage.getClass().getSimpleName();
		return peer.map(p -> {
			if (!checkSignature(signedMessage, p.getSystem())) {
				log.warn("Ignoring {} message from {} - bad signature", messageType, peer);
				this.counters.increment(CounterType.MESSAGES_INBOUND_BADSIGNATURE);
				return false;
			}
			log.trace("Good signature on {} message from {}", messageType, peer);
			return true;
		}).orElse(false);
	}

	private boolean checkSignature(SignedMessage message, RadixSystem system) {
		HashCode hash = hasher.hash(message);
		return system.getKey().verify(hash, message.getSignature());
	}

	private SendResult updateStatistics(SendResult result) {
		this.counters.increment(CounterType.MESSAGES_OUTBOUND_PROCESSED);
		if (result.isComplete()) {
			this.counters.increment(CounterType.MESSAGES_OUTBOUND_SENT);
		}
		return result;
	}

	@SuppressWarnings("resource")
	// Resource warning suppression OK here -> caller is responsible
	private CompletableFuture<TransportOutboundConnection> findTransportAndOpenConnection(
		TransportManager transportManager,
		Peer peer,
		byte[] bytes
	) {
		Transport transport = transportManager.findTransport(peer, bytes);
		return transport.control().open(peer.connectionData(transport.name()));
	}

	private byte[] serialize(Message out) {
		try {
			byte[] uncompressed = serialization.toDson(out, Output.WIRE);
			return Snappy.compress(uncompressed);
		} catch (IOException e) {
			throw new UncheckedIOException("While serializing message", e);
		}
	}

	private String hostId(Optional<PeerWithSystem> peer) {
		return peer.map(this::hostId).orElse("Unknown");
	}

	private String hostId(PeerWithSystem peer) {
		return peer.supportedTransports()
			.findFirst()
			.map(ti -> String.format("%s:%s", ti.name(), ti.metadata()))
			.orElse("None");
	}


	/**
	 * Return true if we already have information about the given peer being banned.
	 * Note that if the peer is already banned according to our address book, the
	 * specified peer instance will have it's banned timestamp updated to match the
	 * known peer's banned time.
	 *
	 * @param peerNid the corresponding node ID of the peer
	 * @param messageType the message type for logging ignored messages
	 * @return {@code true} if the peer is currently banned, {@code false} otherwise
	 */
	private boolean checkPeerBanned(EUID peerNid, String messageType) {
		return this.addressBook.peer(peerNid)
			.filter(kp -> kp.getTimestamp(Timestamps.BANNED) > this.timeSource.currentTime())
			.map(kp -> {
				log.debug("Ignoring {} message from banned peer {}", messageType, kp);
				return true;
			})
			.orElse(false);
	}
}
