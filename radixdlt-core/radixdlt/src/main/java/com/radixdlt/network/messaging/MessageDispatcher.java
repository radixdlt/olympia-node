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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.radixdlt.crypto.Hasher;

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.transport.SendResult;
import com.radixdlt.network.transport.Transport;
import com.radixdlt.network.transport.TransportOutboundConnection;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.utils.Compress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Hex;
import org.radix.network.messages.PeerPingMessage;
import org.radix.network.messaging.Message;
import org.radix.network.messaging.SignedMessage;

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
	private final Hasher hasher;
	private final HashSigner hashSigner;

	MessageDispatcher(
		SystemCounters counters,
		MessageCentralConfiguration config,
		Serialization serialization,
		TimeSupplier timeSource,
		Hasher hasher,
		HashSigner hashSigner
	) {
		this.messageTtlMs = config.messagingTimeToLive(30_000L);
		this.counters = counters;
		this.serialization = serialization;
		this.timeSource = timeSource;
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
			log.info("About to send a signed message of type " + message.getClass().getSimpleName());
			SignedMessage signedMessage = (SignedMessage) message;
			if (signedMessage.getSignature() == null) {
				byte[] hash = hasher.hash(signedMessage).asBytes();
				final var sign = hashSigner.sign(hash);
				log.info("Signed message needs signing, got a hash of: " + Hex.toHexString(hash) + " and a signature: " + sign);
				signedMessage.setSignature(sign);
			} else {
				log.info("Signed message is already signed, no need to sign in dispatcher, signature is: " + signedMessage.getSignature());
			}
		}

		if (message instanceof PeerPingMessage) {
			final var ping = (PeerPingMessage) message;
			log.info("About to send a ping message that should already be signed: " + ping.getSignature() + ", transports = " + ping.getSystem().supportedTransports().collect(Collectors.toList()));
			log.info("Is ping a signed message " + (message instanceof SignedMessage));
		}

		log.info("Got a final message (after signing): " + message);
		byte[] bytes = serialize(message);

		final var ss = new String(bytes);
		log.info("Serialized message = " + ss);

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
		log.warn("{}: {}", msg, cause.getMessage());
		return SendResult.failure(new IOException(msg, cause));
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
			return Compress.compress(uncompressed);
		} catch (IOException e) {
			throw new UncheckedIOException("While serializing message", e);
		}
	}
}
