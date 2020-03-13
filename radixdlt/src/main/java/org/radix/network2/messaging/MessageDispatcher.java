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

package org.radix.network2.messaging;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import org.radix.Radix;

import com.radixdlt.common.EUID;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.DsonOutput.Output;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.Interfaces;
import org.radix.network.messaging.Message;
import org.radix.network.messaging.SignedMessage;
import org.radix.network2.NetworkLegacyPatching;
import org.radix.network2.TimeSupplier;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.transport.SendResult;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.TransportOutboundConnection;
import org.radix.time.Timestamps;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.RadixSystem;
import org.radix.universe.system.SystemMessage;
import org.radix.utils.SystemMetaData;
import org.xerial.snappy.Snappy;

/*
 * This could be moved into MessageCentralImpl at some stage, but has been
 * separated out so that we can check if all the functionality here is
 * required, and remove the stuff we don't want to keep.
 */
class MessageDispatcher {
	private static final Logger log = Logging.getLogger("messaging");

	private final long messageTtlMs;
	private final Serialization serialization;
	private final TimeSupplier timeSource;
	private final LocalSystem localSystem;
	private final Interfaces interfaces;
	private final AddressBook addressBook;

	MessageDispatcher(MessageCentralConfiguration config, Serialization serialization, TimeSupplier timeSource, LocalSystem localSystem, Interfaces interfaces, AddressBook addressBook) {
		this.messageTtlMs = config.messagingTimeToLive(30) * 1000L;
		this.serialization = serialization;
		this.timeSource = timeSource;
		this.localSystem = localSystem;
		this.interfaces = interfaces;
		this.addressBook = addressBook;
	}

	SendResult send(TransportManager transportManager, final MessageEvent outboundMessage) {
		final Message message = outboundMessage.message();
		final Peer peer = outboundMessage.peer();

		if (timeSource.currentTime() - message.getTimestamp() > messageTtlMs) {
			String msg = String.format("%s: TTL to %s has expired", message.getClass().getName(), peer);
			log.warn(msg);
			SystemMetaData.ifPresent( a -> a.increment("messages.outbound.aborted"));
			return SendResult.failure(new IOException(msg));
		}

		try {
			if (message instanceof SignedMessage) {
				SignedMessage signedMessage = (SignedMessage) message;
				if (signedMessage.getSignature() == null) {
					signedMessage.sign(this.localSystem.getKeyPair());
				}
			}

			byte[] bytes = serialize(message);
			return findTransportAndOpenConnection(transportManager, peer, bytes)
				.thenCompose(conn -> conn.send(bytes))
				.thenApply(this::updateStatistics)
				.get();
		} catch (Exception ex) {
			String msg = String.format("%s: Sending to  %s failed", message.getClass().getName(), peer);
			log.error(msg, ex);
			return SendResult.failure(new IOException(msg, ex));
		}
	}

	void receive(MessageListenerList listeners, final MessageEvent inboundMessage) {
		Peer peer = inboundMessage.peer();
		final Message message = inboundMessage.message();

		long currentTime = timeSource.currentTime();
		peer.setTimestamp(Timestamps.ACTIVE, currentTime);
		SystemMetaData.ifPresent(a -> a.increment("messages.inbound.received"));

		if (currentTime - message.getTimestamp() > messageTtlMs) {
			SystemMetaData.ifPresent(a -> a.increment("messages.inbound.discarded"));
			return;
		}

		try {
			if (message instanceof SystemMessage) {
				SystemMessage systemMessage = (SystemMessage) message;
				RadixSystem system = systemMessage.getSystem();

				peer = this.addressBook.updatePeerSystem(peer, system);

				if (system.getNID() == null || EUID.ZERO.equals(system.getNID())) {
					peer.ban(String.format("%s:%s gave null NID", peer, message.getClass().getName()));
					return;
				}

				if (systemMessage.getSystem().getAgentVersion() <= Radix.REFUSE_AGENT_VERSION) {
					peer.ban(String.format("Old peer %s %s:%s", peer, system.getAgent(), system.getProtocolVersion()));
					return;
				}

				if (system.getNID().equals(this.localSystem.getNID())) {
					peer.ban("Message from self");
					TransportInfo ti = inboundMessage.transportInfo();
					if (ti != null) {
						String host = ti.metadata().get("host");
						if (host != null) {
							addInterfaceAddress(interfaces, host); // TODO what about DNS lookups?
						}
					}
					return;
				}

				if (NetworkLegacyPatching.checkPeerBanned(peer, system.getNID(), timeSource, this.addressBook)) {
					return;
				}
			}
		} catch (Exception ex) {
			log.error(inboundMessage.message().getClass().getName() + ": Pre-processing from " + inboundMessage.peer() + " failed", ex);
			return;
		}

		listeners.messageReceived(peer, message);
		SystemMetaData.ifPresent( a -> a.increment("messages.inbound.processed"));
	}

	private SendResult updateStatistics(SendResult result) {
		SystemMetaData.ifPresent( a -> a.increment("messages.outbound.processed"));
		if (result.isComplete()) {
			SystemMetaData.ifPresent( a -> a.increment("messages.outbound.sent"));
		}
		return result;
	}

	private void addInterfaceAddress(Interfaces interfaces, String host) {
		try {
			interfaces.addInterfaceAddress(InetAddress.getByName(host));
		} catch (UnknownHostException e) {
			log.warn("Host name lookup failed", e);
		}
	}

	@SuppressWarnings("resource")
	// Resource warning suppression OK here -> caller is responsible
	private CompletableFuture<TransportOutboundConnection> findTransportAndOpenConnection(TransportManager transportManager, Peer peer, byte[] bytes) {
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
}
