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

import com.google.common.hash.HashCode;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.Radix;
import org.radix.network.messaging.Message;
import org.radix.network.messaging.SignedMessage;
import org.radix.time.Timestamps;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.RadixSystem;
import org.radix.universe.system.SystemMessage;
import org.xerial.snappy.Snappy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Handles incoming messages. Deserializes raw messages and validates them.
 */
class MessagePreprocessor {
	private static final Logger log = LogManager.getLogger();

	private final long messageTtlMs;
	private final SystemCounters counters;
	private final TimeSupplier timeSource;
	private final LocalSystem localSystem;
	private final AddressBook addressBook;
	private final Hasher hasher;
	private final Serialization serialization;

	MessagePreprocessor(
		SystemCounters counters,
		MessageCentralConfiguration config,
		TimeSupplier timeSource,
		LocalSystem localSystem,
		AddressBook addressBook,
		Hasher hasher,
		Serialization serialization
	) {
		this.messageTtlMs = config.messagingTimeToLive(30_000L);
		this.counters = counters;
		this.timeSource = timeSource;
		this.localSystem = localSystem;
		this.addressBook = addressBook;
		this.hasher = hasher;
		this.serialization = serialization;
	}

	Optional<Pair<Peer, Message>> process(InboundMessage inboundMessage) {
		final byte[] messageBytes = inboundMessage.message();
		this.counters.add(CounterType.NETWORKING_RECEIVED_BYTES, messageBytes.length);
		final Message message = deserialize(messageBytes);
		return processMessage(inboundMessage.source(), message);
	}

	Optional<Pair<Peer, Message>> processMessage(TransportInfo source, Message message) {
		Optional<PeerWithSystem> peer = this.addressBook.peer(source);

		final long currentTime = timeSource.currentTime();
		peer.ifPresent(p -> p.setTimestamp(Timestamps.ACTIVE, currentTime));
		this.counters.increment(CounterType.MESSAGES_INBOUND_RECEIVED);

		final boolean isBanned = peer.map(Peer::isBanned).orElse(false);
		if (isBanned || currentTime - message.getTimestamp() > messageTtlMs) {
			this.counters.increment(CounterType.MESSAGES_INBOUND_DISCARDED);
			return Optional.empty();
		}

		try {
			if (message instanceof SystemMessage) {
				peer = handleSystemMessage(peer, source, (SystemMessage) message);
				if (!peer.isPresent()) {
					return Optional.empty();
				}
			} else if (message instanceof SignedMessage && !handleSignedMessage(peer, (SignedMessage) message)) {
				return Optional.empty();
			}
		} catch (Exception ex) {
			String msg = String.format("%s: Pre-processing from %s failed", message.getClass().getSimpleName(), source);
			log.error(msg, ex);
			return Optional.empty();
		}

		if (log.isTraceEnabled()) {
			log.trace("Received from {}: {}", hostId(peer), message);
		}

		return peer.map(p -> Pair.of(p, message));
	}

	private Message deserialize(byte[] in) {
		try {
			byte[] uncompressed = Snappy.uncompress(in);
			return serialization.fromDson(uncompressed, Message.class);
		} catch (IOException e) {
			throw new UncheckedIOException("While deserializing message", e);
		}
	}

	private Optional<PeerWithSystem> handleSystemMessage(
		Optional<PeerWithSystem> oldPeer,
		TransportInfo source,
		SystemMessage systemMessage
	) {
		String messageType = systemMessage.getClass().getSimpleName();
		RadixSystem system = systemMessage.getSystem();
		if (checkSignature(systemMessage, system)) {
			PeerWithSystem peer = this.addressBook.addOrUpdatePeer(oldPeer, system, source);
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

	private String hostId(Optional<PeerWithSystem> peer) {
		return peer.map(this::hostId).orElse("Unknown");
	}

	private String hostId(Peer peer) {
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
