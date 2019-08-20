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
import org.radix.network.peers.Peer;
import org.radix.network.peers.PeerStore;
import org.radix.network2.transport.SendResult;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportOutboundConnection;
import org.radix.state.State;
import org.radix.time.Time;
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
//FIXME: Optional dependency on Modules.get(Interfaces.class) for keeping track of network interfaces
//FIXME: Dependency on Modules.get(PeerStore.class) for keeping track of peers
// FIXME: Dependency on static Time.currentTimestamp() for millisecond time
// FIXME: Dependency on LocalSystem.getInstance() for signing key
class MessageDispatcher {
	private static final Logger log = Logging.getLogger("messaging");

	private final long messageTtlMs;
	private final Serialization serialization;

	MessageDispatcher(MessageCentralConfiguration config, Serialization serialization) {
		this.messageTtlMs = config.messagingTimeToLive(30) * 1000L;
		this.serialization = serialization;
	}

	void send(TransportManager transportManager, final MessageEvent outboundMessage) {
		long start = SystemProfiler.getInstance().begin();
		final Message message = outboundMessage.message();
		final Peer peer = outboundMessage.peer();
		final State peerState = peer.getState();

		if (Time.currentTimestamp() - message.getTimestamp() > messageTtlMs) {
			log.warn(message.getClass().getName() + ": TTL to " + peer.getURI() + " has expired");
			Modules.ifAvailable(SystemMetaData.class, a -> a.increment("messages.outbound.aborted"));
			return;
		}

		if (peerState.in(State.DISCONNECTED) || peerState.in(State.DISCONNECTING)) {
			log.warn(message.getClass().getName() + ": peer " + peer.getURI() + " is " + peerState);
			Modules.ifAvailable(SystemMetaData.class, a -> a.increment("messages.outbound.aborted"));
			return;
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
			findTransportAndOpenConnection(transportManager, peer, bytes)
				.thenCompose(conn -> conn.send(bytes))
				.thenApply(this::updateStatistics);
		} catch (Exception ex) {
			log.error(message.getClass().getName() + ": Sending to " + peer.getURI() + " failed", ex);
		} finally {
			SystemProfiler.getInstance().incrementFrom("MESSAGING:SEND:"+message.getCommand(), start);
		}
	}

	void receive(MessageListenerList listeners, final MessageEvent inboundMessage) {
		final Peer peer = inboundMessage.peer();
		final Message message = inboundMessage.message();

		peer.setTimestamp(Timestamps.ACTIVE, Time.currentTimestamp());
		Modules.ifAvailable(SystemMetaData.class, a -> a.increment("messages.inbound.received"));

		State peerState = peer.getState();

		// Do we really want to do this?
		if (peerState.in(State.DISCONNECTING) || peerState.in(State.DISCONNECTED)) {
			return;
		}

		if (Time.currentTimestamp() - message.getTimestamp() > messageTtlMs) {
			Modules.ifAvailable(SystemMetaData.class, a -> a.increment("messages.inbound.discarded"));
			return;
		}

		try {
			if (message instanceof SystemMessage) {
				SystemMessage systemMessage = (SystemMessage) message;
				RadixSystem system = systemMessage.getSystem();

				// TODO this feels dirty here
				if (system.getClock().get() < peer.getSystem().getClock().get()) {
					log.error("IMPLEMENT CLOCK MANIPULATION CHECK!");
				}
				peer.setSystem(system);

				if (system.getNID() == null || EUID.ZERO.equals(system.getNID())) {
					peer.disconnect(String.format("%s:%s gave null NID", peer.getURI(), message.getClass().getName()));
					return;
				}

				if (systemMessage.getSystem().getAgentVersion() <= Radix.REFUSE_AGENT_VERSION) {
					peer.disconnect(String.format("Old peer %s %s:%s", peer.getURI(), system.getAgent(), system.getProtocolVersion()));
					return;
				}

				if (system.getNID().equals(LocalSystem.getInstance().getNID())) {
					peer.ban("Message from self");
					Modules.ifAvailable(Interfaces.class, i -> addInterfaceAddress(i, peer)); // TODO what about DNS lookups?
					return;
				}

				if (!peerState.in(State.CONNECTED)) {
					Peer knownPeer = Modules.get(PeerStore.class).getPeer(system.getNID());

					if (knownPeer != null && knownPeer.getTimestamp(Timestamps.BANNED) > Time.currentTimestamp()) {
						peer.setTimestamp(Timestamps.BANNED, knownPeer.getTimestamp(Timestamps.BANNED));
						peer.setBanReason(knownPeer.getBanReason());
						peer.ban(String.format("Banned peer %s at %s", system.getNID(), peer.toString()));
						return;
					}
				}
			}
		} catch (Exception ex) {
			log.error(inboundMessage.message().getClass().getName() + ": Pre-processing from " + inboundMessage.peer().getURI() + " failed", ex);
			return;
		}

		long start = SystemProfiler.getInstance().begin();
		try {
			Modules.ifAvailable(MessageProfiler.class, mp -> mp.process(message, peer));
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

	private void addInterfaceAddress(Interfaces interfaces, Peer peer) {
		try {
			interfaces.addInterfaceAddress(InetAddress.getByName(peer.getURI().getHost()));
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

	private <T extends Message> MessageListener<Message> untyped(MessageListener<T> listener) {
		// FIXME: Type abuse
		return (MessageListener<Message>) listener;
	}
}
