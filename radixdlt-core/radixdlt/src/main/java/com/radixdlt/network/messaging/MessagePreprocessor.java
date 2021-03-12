/*
 * (C) Copyright 2021 Radix DLT Ltd
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

import io.vavr.control.Either;
import lombok.Value;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.Radix;
import org.radix.network.messaging.Message;
import org.radix.network.messaging.SignedMessage;
import org.radix.time.Timestamps;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.RadixSystem;
import org.radix.universe.system.SystemMessage;

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
import com.radixdlt.utils.Compress;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Handles incoming messages. Deserializes raw messages and validates them.
 */
final class MessagePreprocessor {
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

	Either<MessageProcessingError, MessageFromPeer<Message>> process(InboundMessage inboundMessage) {
		final byte[] messageBytes = inboundMessage.message();
		this.counters.add(CounterType.NETWORKING_RECEIVED_BYTES, messageBytes.length);
		final Message message = deserialize(messageBytes);
		final var result = processMessage(inboundMessage.source(), message);

		this.counters.increment(CounterType.MESSAGES_INBOUND_RECEIVED);
		this.counters.increment(result.isRight() ? CounterType.MESSAGES_INBOUND_PROCESSED : CounterType.MESSAGES_INBOUND_DISCARDED);

		return result;
	}

	Either<MessageProcessingError, MessageFromPeer<Message>> processMessage(TransportInfo source, Message message) {
		final var maybeExistingPeer = this.addressBook.peer(source);

		final var currentTime = timeSource.currentTime();
		maybeExistingPeer.ifPresent(p -> p.setTimestamp(Timestamps.ACTIVE, currentTime));

		final var isBanned = maybeExistingPeer.map(Peer::isBanned).orElse(false);
		if (isBanned || currentTime - message.getTimestamp() > messageTtlMs) {
			return Either.left(MessageProcessingError.of("Peer is banned"));
		}

		if (message instanceof SystemMessage) {
			final var maybeUpdatedPeer = handleSystemMessage(maybeExistingPeer, source, (SystemMessage) message);
			return maybeUpdatedPeer.map(updatedPeer -> new MessageFromPeer<>(updatedPeer, message));
		} else if (maybeExistingPeer.isEmpty()) {
			// the only case where peer can be empty is a SystemMessage
			return Either.left(MessageProcessingError.of("Peer not present in address book"));
		} else {
			// peer is present and this is not a SystemMessage, just check the signature
			final var peer = maybeExistingPeer.get();
			if (message instanceof SignedMessage && !checkSignature((SignedMessage) message, peer.getSystem())) {
				this.counters.increment(CounterType.MESSAGES_INBOUND_BADSIGNATURE);
				return Either.left(MessageProcessingError.of("Invalid signature"));
			} else {
				// all good
				return Either.right(new MessageFromPeer<>(peer, message));
			}
		}
	}

	private Message deserialize(byte[] in) {
		try {
			byte[] uncompressed = Compress.uncompress(in);
			return serialization.fromDson(uncompressed, Message.class);
		} catch (IOException e) {
			throw new UncheckedIOException("While deserializing message", e);
		}
	}

	private Either<MessageProcessingError, PeerWithSystem> handleSystemMessage(
		Optional<PeerWithSystem> maybeExistingPeer,
		TransportInfo source,
		SystemMessage systemMessage
	) {
		final var messageType = systemMessage.getClass().getSimpleName();
		final var system = systemMessage.getSystem();

		if (!checkSignature(systemMessage, system)) {
			this.counters.increment(CounterType.MESSAGES_INBOUND_BADSIGNATURE);
			return Either.left(MessageProcessingError.of("Bad signature"));
		}

		final var updatedPeer = this.addressBook.addOrUpdatePeer(maybeExistingPeer, system, source);
		log.trace("Good signature on {} from {}", messageType, updatedPeer);

		if (system.getNID() == null || EUID.ZERO.equals(system.getNID())) {
			updatedPeer.ban(String.format("%s:%s gave null NID", updatedPeer, messageType));
			return Either.left(MessageProcessingError.of("Null NID"));
		}

		if (systemMessage.getSystem().getAgentVersion() <= Radix.REFUSE_AGENT_VERSION) {
			updatedPeer.ban(String.format("Old peer %s %s:%s", updatedPeer, system.getAgent(), system.getProtocolVersion()));
			return Either.left(MessageProcessingError.of("Invalid agent version"));
		}

		if (system.getNID().equals(this.localSystem.getNID())) {
			return Either.left(MessageProcessingError.of("Message from self"));
		}

		if (checkPeerBanned(system.getNID(), messageType)) {
			return Either.left(MessageProcessingError.of("Peer banned"));
		}

		return Either.right(updatedPeer);
	}

	private boolean checkSignature(SignedMessage message, RadixSystem system) {
		final var hash = hasher.hash(message);
		return system.getKey().verify(hash, message.getSignature());
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
			.isPresent();
	}

	@Value(staticConstructor = "of")
	static class MessageProcessingError {
		String msg;
	}
}
