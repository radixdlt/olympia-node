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

import com.google.inject.Provider;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.PeerControl;
import com.radixdlt.utils.functional.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network.messaging.Message;

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.utils.TimeSupplier;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Compress;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

import static com.radixdlt.network.messaging.MessagingErrors.IO_ERROR;
import static com.radixdlt.network.messaging.MessagingErrors.MESSAGE_EXPIRED;

/**
 * Handles incoming messages. Deserializes raw messages and validates them.
 */
final class MessagePreprocessor {
	private static final Logger log = LogManager.getLogger();

	private final long messageTtlMs;
	private final SystemCounters counters;
	private final TimeSupplier timeSource;
	private final Serialization serialization;
	private final Provider<PeerControl> peerControl;

	MessagePreprocessor(
		SystemCounters counters,
		MessageCentralConfiguration config,
		TimeSupplier timeSource,
		Serialization serialization,
		Provider<PeerControl> peerControl
	) {
		this.messageTtlMs = Objects.requireNonNull(config).messagingTimeToLive(30_000L);
		this.counters = Objects.requireNonNull(counters);
		this.timeSource = Objects.requireNonNull(timeSource);
		this.serialization = Objects.requireNonNull(serialization);
		this.peerControl = Objects.requireNonNull(peerControl);
	}

	Result<MessageFromPeer<Message>> process(InboundMessage inboundMessage) {
		final byte[] messageBytes = inboundMessage.message();
		this.counters.add(CounterType.NETWORKING_RECEIVED_BYTES, messageBytes.length);
		final var result = deserialize(inboundMessage, messageBytes)
			.flatMap(message -> processMessage(inboundMessage.source(), message));
		this.counters.increment(CounterType.MESSAGES_INBOUND_RECEIVED);
		result.fold(
			unused -> this.counters.increment(CounterType.MESSAGES_INBOUND_DISCARDED),
			unused -> this.counters.increment(CounterType.MESSAGES_INBOUND_PROCESSED)
		);
		return result;
	}

	Result<MessageFromPeer<Message>> processMessage(NodeId source, Message message) {
		final var currentTime = timeSource.currentTime();

		if (currentTime - message.getTimestamp() > messageTtlMs) {
			return MESSAGE_EXPIRED.result();
		} else {
			return Result.ok(new MessageFromPeer<>(source, message));
		}
	}

	private Result<Message> deserialize(InboundMessage inboundMessage, byte[] in) {
		try {
			byte[] uncompressed = Compress.uncompress(in);
			return Result.ok(serialization.fromDson(uncompressed, Message.class));
		} catch (IOException e) {
			log.error(String.format("Failed to deserialize message from peer %s", inboundMessage.source()), e);
			peerControl.get().banPeer(inboundMessage.source(), Duration.ofMinutes(5), "Failed to deserialize inbound message");
			return IO_ERROR.result();
		}
	}
}
