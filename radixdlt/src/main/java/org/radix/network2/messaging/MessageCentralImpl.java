package org.radix.network2.messaging;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network.Network;
import org.radix.network.Protocol;
import org.radix.network.exceptions.BanException;
import org.radix.network.messaging.Message;
import org.radix.network.messaging.Messaging;
import org.radix.network.peers.UDPPeer;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.transport.SendResult;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.TransportListener;
import org.radix.network2.transport.TransportOutboundConnection;
import org.radix.properties.RuntimeProperties;

import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.universe.Universe;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;


class MessageCentralImpl implements MessageCentral, Closeable {
	private static final Logger log = Logging.getLogger("message");

	private static final int INBOUND_PACKET_QUEUE_LENGTH = Modules.get(RuntimeProperties.class).get("messaging.inbound.queue_max", 8192);

	// Dependencies
	private final Serialization serialization;
	private final ConnectionManager connectionManager;

	// Local data

	// Listeners we are managing
	private final List<TransportListener> transportListeners;

	// Limit rate at which dropped packet logs are produced
	private final RateLimiter droppedPacketLogRateLimiter = RateLimiter.create(1.0);

	// Message handling
	private final BlockingQueue<InboundMessage> receiveQueue = new LinkedBlockingQueue<>(INBOUND_PACKET_QUEUE_LENGTH);
	private final Thread processingThread;


	MessageCentralImpl(
		Serialization serialization,
		ConnectionManager connectionManager,
		Iterable<TransportListener> listeners
	) {
		this.serialization = Objects.requireNonNull(serialization);
		this.connectionManager = Objects.requireNonNull(connectionManager);

		this.transportListeners = Lists.newArrayList(Objects.requireNonNull(listeners));

		if (this.transportListeners.isEmpty()) {
			// This will work, but it will be a quiet life indeed.
			this.processingThread = null;
			log.warn("No transport listeners supplied");
		} else {
			// Start processing thread
			this.processingThread = new Thread(this::messageProcessor, getClass().getSimpleName() + " message processing");
			this.processingThread.setDaemon(true);
			this.processingThread.start();
		}

		// Start our listeners
		this.transportListeners.forEach(tl -> tl.start(this::inboundMessage));
	}

	@Override
	public void close() {
		this.transportListeners.forEach(tl -> closeWithLog(tl));
		this.transportListeners.clear();

		if (processingThread != null) {
			processingThread.interrupt();
			try {
				processingThread.join();
			} catch (InterruptedException e) {
				log.error(processingThread.getName() + " did not exit before interrupt");
			}
		}
	}

	private void closeWithLog(TransportListener tl) {
		try {
			tl.close();
		} catch (IOException e) {
			log.error("Error closing transport listener " + tl, e);
		}
	}

	@Override
	public CompletableFuture<SendResult> send(Peer peer, Message message) {
		byte[] bytes = serialize(message);
		return findTransportAndOpenConnection(peer, bytes).thenCompose(conn -> conn.send(bytes));
	}

	@Override
	public <T extends Message> boolean addListener(Class<T> messageType, MessageListener<T> listener) {
		// FIXME: At the moment inbound messages are routed via Messaging in messageProcessor
		throw new IllegalStateException();
	}

	@Override
	public <T extends Message> boolean removeListener(Class<T> messageType, MessageListener<T> listener) {
		// FIXME: At the moment inbound messages are routed via Messaging in messageProcessor
		throw new IllegalStateException();
	}

	private byte[] serialize(Object out) {
		try {
			return serialization.toDson(out, Output.WIRE);
		} catch (SerializationException e) {
			throw new UncheckedSerializationException("While serializing message", e);
		}
	}

	private CompletableFuture<TransportOutboundConnection> findTransportAndOpenConnection(Peer peer, byte[] bytes) {
		return connectionManager.findTransport(peer, bytes).control().open(peer);
	}

	private void inboundMessage(InboundMessage message) {
		if (!receiveQueue.offer(message) && droppedPacketLogRateLimiter.tryAcquire()) {
			log.error(String.format("%s message from %s dropped", message.source().name(), message.source().metadata()));
		}
	}

	private void messageProcessor() {
		try {
			for (;;) {
				final InboundMessage inbound = receiveQueue.take();
				final byte[] data = inbound.message();
				final TransportInfo source = inbound.source();

				try {
					// FIXME: This needs replacing - at the moment just hooking up to existing infra
					String peerAddress = source.metadata().get("host");
					URI uri = URI.create(Network.URI_PREFIX + peerAddress + ":" + Modules.get(Universe.class).getPort());
					UDPPeer peer = Network.getInstance().connect(uri, Protocol.UDP);
					Message message = Message.parse(new ByteArrayInputStream(data, 0, data.length));
					Messaging.getInstance().received(message, peer);
				} catch (IOException ex) {
					log.error(String.format("While processing inbound message from %s %s", source.name(), source.metadata()), ex);
				} catch (BanException ex) {
					// FIXME: Not quite sure what to do with this just yet
					log.error(String.format("Peer at %s %s should be banned", source.name(), source.metadata()), ex);
				}
			}
		} catch (InterruptedException e) {
			// Just exit
			Thread.currentThread().interrupt();
		}
	}
}
