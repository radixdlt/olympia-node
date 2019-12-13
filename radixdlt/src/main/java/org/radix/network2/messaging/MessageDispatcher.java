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
import org.radix.modules.Modules;
import org.radix.network.Interfaces;
import org.radix.network.messaging.Message;
import org.radix.network.messaging.Message.Direction;
import org.radix.network.messaging.MessageProfiler;
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
import org.radix.utils.SystemProfiler;
import org.xerial.snappy.Snappy;

/*
 * This could be moved into MessageCentralImpl at some stage, but has been
 * separated out so that we can check if all the functionality here is
 * required, and remove the stuff we don't want to keep.
 */
//FIXME: Optional dependency on Modules.get(SystemMetaData.class) for system metadata
//FIXME: Optional dependency on Modules.get(MessageProfiler.class) for profiling
//FIXME: Optional dependency on Modules.get(AddressBook.class) for profiling
//FIXME: Optional dependency on Modules.get(Interfaces.class) for keeping track of network interfaces
// FIXME: Dependency on LocalSystem.getInstance() for signing key
class MessageDispatcher {
	private static final Logger log = Logging.getLogger("messaging");

	private final long messageTtlMs;
	private final Serialization serialization;
	private final TimeSupplier timeSource;

	MessageDispatcher(MessageCentralConfiguration config, Serialization serialization, TimeSupplier timeSource) {
		this.messageTtlMs = config.messagingTimeToLive(30) * 1000L;
		this.serialization = serialization;
		this.timeSource = timeSource;
	}

	SendResult send(TransportManager transportManager, final MessageEvent outboundMessage) {
		long start = SystemProfiler.getInstance().begin();
		final Message message = outboundMessage.message();
		final Peer peer = outboundMessage.peer();

		if (timeSource.currentTime() - message.getTimestamp() > messageTtlMs) {
			String msg = String.format("%s: TTL to %s has expired", message.getClass().getName(), peer);
			log.warn(msg);
			Modules.ifAvailable(SystemMetaData.class, a -> a.increment("messages.outbound.aborted"));
			return SendResult.failure(new IOException(msg));
		}

		Modules.ifAvailable(MessageProfiler.class, mp -> mp.process(message, peer));
		message.setDirection(Direction.OUTBOUND);

		try {
			if (message instanceof SignedMessage) {
				SignedMessage signedMessage = (SignedMessage) message;
				if (signedMessage.getSignature() == null) {
					signedMessage.sign(LocalSystem.getInstance().getKeyPair());
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
		} finally {
			SystemProfiler.getInstance().incrementFrom("MESSAGING:SEND:"+message.getCommand(), start);
		}
	}

	void receive(MessageListenerList listeners, final MessageEvent inboundMessage) {
		Peer peer = inboundMessage.peer();
		final Message message = inboundMessage.message();

		long currentTime = timeSource.currentTime();
		peer.setTimestamp(Timestamps.ACTIVE, currentTime);
		Modules.ifAvailable(SystemMetaData.class, a -> a.increment("messages.inbound.received"));

		if (currentTime - message.getTimestamp() > messageTtlMs) {
			Modules.ifAvailable(SystemMetaData.class, a -> a.increment("messages.inbound.discarded"));
			return;
		}

		try {
			if (message instanceof SystemMessage) {
				SystemMessage systemMessage = (SystemMessage) message;
				RadixSystem system = systemMessage.getSystem();

				peer = Modules.get(AddressBook.class).updatePeerSystem(peer, system);

				if (system.getNID() == null || EUID.ZERO.equals(system.getNID())) {
					peer.ban(String.format("%s:%s gave null NID", peer, message.getClass().getName()));
					return;
				}

				if (systemMessage.getSystem().getAgentVersion() <= Radix.REFUSE_AGENT_VERSION) {
					peer.ban(String.format("Old peer %s %s:%s", peer, system.getAgent(), system.getProtocolVersion()));
					return;
				}

				if (system.getNID().equals(LocalSystem.getInstance().getNID())) {
					peer.ban("Message from self");
					TransportInfo ti = inboundMessage.transportInfo();
					if (ti != null) {
						String host = ti.metadata().get("host");
						if (host != null) {
							Modules.ifAvailable(Interfaces.class, i -> addInterfaceAddress(i, host)); // TODO what about DNS lookups?
						}
					}
					return;
				}

				if (NetworkLegacyPatching.checkPeerBanned(peer, system.getNID(), timeSource)) {
					return;
				}
			}
		} catch (Exception ex) {
			log.error(inboundMessage.message().getClass().getName() + ": Pre-processing from " + inboundMessage.peer() + " failed", ex);
			return;
		}

		long start = SystemProfiler.getInstance().begin();
		try {
			final Peer fp = peer; // Awkward
			Modules.ifAvailable(MessageProfiler.class, mp -> mp.process(message, fp));
			listeners.messageReceived(peer, message);
			Modules.ifAvailable(SystemMetaData.class, a -> a.increment("messages.inbound.processed"));
		} finally {
			SystemProfiler.getInstance().incrementFrom("MESSAGING:IN:" + message.getCommand(), start);
			SystemProfiler.getInstance().incrementFrom("MESSAGING:IN", start);
		}
	}

	private SendResult updateStatistics(SendResult result) {
		Modules.ifAvailable(SystemMetaData.class, a -> a.increment("messages.outbound.processed"));
		if (result.isComplete()) {
			Modules.ifAvailable(SystemMetaData.class, a -> a.increment("messages.outbound.sent"));
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
