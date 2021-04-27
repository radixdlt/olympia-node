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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.utils.TimeSupplier;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.transport.PeerChannel;
import com.radixdlt.network.p2p.PeerManager;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.utils.Compress;

import com.radixdlt.utils.functional.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network.messaging.Message;

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
	private final PeerManager peerManager;

	MessageDispatcher(
		SystemCounters counters,
		MessageCentralConfiguration config,
		Serialization serialization,
		TimeSupplier timeSource,
		PeerManager peerManager
	) {
		this.messageTtlMs = config.messagingTimeToLive(30_000L);
		this.counters = counters;
		this.serialization = serialization;
		this.timeSource = timeSource;
		this.peerManager = peerManager;
	}

	CompletableFuture<Result<Object>> send(final OutboundMessageEvent outboundMessage) {
		final var message = outboundMessage.message();
		final var receiver = outboundMessage.receiver();

		if (timeSource.currentTime() - message.getTimestamp() > messageTtlMs) {
			String msg = String.format("TTL for %s message to %s has expired", message.getClass().getSimpleName(), receiver);
			log.warn(msg);
			this.counters.increment(CounterType.MESSAGES_OUTBOUND_ABORTED);
			return CompletableFuture.completedFuture(Result.fail(new IOException(msg)));
		}

		final var bytes = serialize(message);

		return peerManager.findOrCreateChannel(outboundMessage.receiver())
			.thenApply(channel -> send(channel, bytes))
			.thenApply(this::updateStatistics)
			.exceptionally(t -> completionException(t, receiver, message));
	}

	private Result<Object> send(PeerChannel channel, byte[] bytes) {
		this.counters.add(CounterType.NETWORKING_SENT_BYTES, bytes.length);
		return channel.send(bytes);
	}

	private Result<Object> completionException(Throwable cause, NodeId receiver, Message message) {
		final var msg = String.format("Send %s to %s failed", message.getClass().getSimpleName(), receiver);
		log.warn("{}: {}", msg, cause.getMessage());
		return Result.fail(new IOException(msg, cause));
	}

	private Result<Object> updateStatistics(Result<Object> result) {
		this.counters.increment(CounterType.MESSAGES_OUTBOUND_PROCESSED);
		if (result.isSuccess()) {
			this.counters.increment(CounterType.MESSAGES_OUTBOUND_SENT);
		}
		return result;
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
