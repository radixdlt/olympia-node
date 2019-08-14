package org.radix.network2.messaging;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network.Network;
import org.radix.network.Protocol;
import org.radix.network.messaging.Message;
import org.radix.network.peers.Peer;
import org.radix.network.peers.UDPPeer;
import org.radix.network2.transport.TransportListener;
import org.radix.properties.RuntimeProperties;
import org.radix.universe.system.events.QueueFullEvent;
import org.radix.utils.SystemMetaData;
import org.xerial.snappy.Snappy;

import com.radixdlt.universe.Universe;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;
import com.radixdlt.serialization.Serialization;

//FIXME: Dependency on Modules.get(RuntimeProperties.class) for queue length
//FIXME: Dependency on Modules.get(SystemMetadata.class) for updating metadata
// FIXME: Dependency on Events.getInstance() for queue full messages
// FIXME: Dependency on Network.getInstance() for peer handling
// FIXME: Dependency on Messaging.getInstance() for message processing
public final class MessageCentralImpl implements MessageCentral, Closeable {
	private static final Logger log = Logging.getLogger("message");

	private static final int INBOUND_PACKET_QUEUE_LENGTH = Modules.get(RuntimeProperties.class).get("messaging.inbound.queue_max", 8192);
	private static final int OUTBOUND_PACKET_QUEUE_LENGTH = Modules.get(RuntimeProperties.class).get("messaging.outbound.queue_max", 16384);

	private static final MessageListenerList EMPTY_MESSAGE_LISTENER_LIST = new MessageListenerList();

	// Dependencies
	private final Serialization serialization;
	private final ConnectionManager connectionManager;

	// Local data

	// Message dispatching
	private final MessageDispatcher messageDispatcher;

	// Listeners
	private final ConcurrentHashMap<Class<? extends Message>, MessageListenerList> listeners = new ConcurrentHashMap<>();

	// Out time base for System.nanoTime() differences.  Per documentation can only compare deltas
	private final long timeBase = System.nanoTime();

	// Listeners we are managing
	private final List<TransportListener> transportListeners;

	// Limit rate at which dropped packet logs are produced
	private final RateLimiter inboundLogRateLimiter = RateLimiter.create(1.0);
	private final RateLimiter outboundLogRateLimiter = RateLimiter.create(1.0);

	// Inbound message handling
	private final PriorityBlockingQueue<MessageEvent> inboundQueue = new PriorityBlockingQueue<>(INBOUND_PACKET_QUEUE_LENGTH);
	private final Thread inboundProcessingThread;

	// Outbound message handling
	private final PriorityBlockingQueue<MessageEvent> outboundQueue = new PriorityBlockingQueue<>(OUTBOUND_PACKET_QUEUE_LENGTH);
	private final Thread outboundProcessingThread;


	public MessageCentralImpl(
		Serialization serialization,
		ConnectionManager connectionManager,
		Iterable<TransportListener> listeners
	) {
		this.serialization = Objects.requireNonNull(serialization);
		this.messageDispatcher = new MessageDispatcher(serialization); // FIXME: Probably should be injected dependency
		this.connectionManager = Objects.requireNonNull(connectionManager);

		this.transportListeners = Lists.newArrayList(Objects.requireNonNull(listeners));

		if (this.transportListeners.isEmpty()) {
			// This will work, but it will be a quiet life indeed.
			this.inboundProcessingThread = null;
			log.warn("No transport listeners supplied");
		} else {
			// Start inbound processing thread
			this.inboundProcessingThread = new Thread(this::inboundMessageProcessor, getClass().getSimpleName() + " inbound message processing");
			this.inboundProcessingThread.setDaemon(true);
			this.inboundProcessingThread.start();
		}

		// Start outbound processing thread
		this.outboundProcessingThread = new Thread(this::outboundMessageProcessor, getClass().getSimpleName() + " outbound message processing");
		this.outboundProcessingThread.setDaemon(true);
		this.outboundProcessingThread.start();

		// Start our listeners
		this.transportListeners.forEach(tl -> tl.start(this::inboundMessage));
	}

	@Override
	public void close() {
		this.transportListeners.forEach(tl -> closeWithLog(tl));
		this.transportListeners.clear();

		if (inboundProcessingThread != null) {
			inboundProcessingThread.interrupt();
			try {
				inboundProcessingThread.join();
			} catch (InterruptedException e) {
				log.error(inboundProcessingThread.getName() + " did not exit before interrupt");
			}
		}
	}

	@Override
	public void send(Peer peer, Message message) {
		if (!outboundQueue.offer(new MessageEvent(peer, message, System.nanoTime() - timeBase))) {
			if (outboundLogRateLimiter.tryAcquire()) {
				log.error(String.format("Outbound message to %s dropped", peer.getURI()));
			}
			Events.getInstance().broadcast(new QueueFullEvent());
		}
	}

	@Override
	public void inject(Peer peer, Message message) {
		MessageEvent event = new MessageEvent(peer, message, System.nanoTime() - timeBase);
		if (!inboundQueue.offer(event)) {
			if (inboundLogRateLimiter.tryAcquire()) {
				log.error(String.format("Injected message from %s dropped", peer.getURI()));
			}
			Events.getInstance().broadcast(new QueueFullEvent());
		}
	}

	@Override
	public <T extends Message> void addListener(Class<T> messageType, MessageListener<T> listener) {
		Objects.requireNonNull(messageType);
		this.listeners.computeIfAbsent(messageType, k -> new MessageListenerList()).addMessageListener(listener);
	}

	@Override
	public <T extends Message> void removeListener(Class<T> messageType, MessageListener<T> listener) {
		Objects.requireNonNull(messageType);
		this.listeners.getOrDefault(messageType, EMPTY_MESSAGE_LISTENER_LIST).removeMessageListener(listener);
	}

	@Override
	public <T extends Message> void removeListener(MessageListener<T> listener) {
		this.listeners.values().forEach(mll -> mll.removeMessageListener(listener));
	}

	@VisibleForTesting
	boolean hasInboundThread() {
		return this.inboundProcessingThread != null;
	}

	@VisibleForTesting
	int listenersSize() {
		return listeners.values().stream().mapToInt(MessageListenerList::size).sum();
	}

	private void inboundMessage(InboundMessage inboundMessage) {
		// FIXME: This needs replacing - at the moment just hooking up to existing infra
		String peerAddress = inboundMessage.source().metadata().get("host");
		URI uri = URI.create(Network.URI_PREFIX + peerAddress + ":" + Modules.get(Universe.class).getPort());
		try {
			UDPPeer peer = Network.getInstance().connect(uri, Protocol.UDP);
			Message message = deserialize(inboundMessage.message());
			MessageEvent event = new MessageEvent(peer, message, System.nanoTime() - timeBase);
			if (!inboundQueue.offer(event)) {
				if (inboundLogRateLimiter.tryAcquire()) {
					log.error(String.format("Inbound %s message from %s dropped", inboundMessage.source().name(), inboundMessage.source().metadata()));
				}
				Events.getInstance().broadcast(new QueueFullEvent());
			}
		} catch (IOException e) {
			throw new UncheckedIOException("While processing inbound message from " + inboundMessage.source(), e);
		}
	}

	private void inboundMessageProcessor() {
		for (;;) {
			try {
				final MessageEvent inbound = inboundQueue.take();
				Modules.ifAvailable(SystemMetaData.class, a -> a.put("messages.inbound.pending", inboundQueue.size()));
				MessageListenerList listeners = this.listeners.getOrDefault(inbound.message().getClass(), EMPTY_MESSAGE_LISTENER_LIST);
				messageDispatcher.receive(listeners, inbound);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				// Exit loop
				break;
			} catch (Exception e) {
				// Don't really want this thread to exit, even if an exception does occur
				log.error("While processing inbound message", e);
			}
		}
	}

	private void outboundMessageProcessor() {
		for (;;) {
			try {
				final MessageEvent outbound = outboundQueue.take();
				Modules.ifAvailable(SystemMetaData.class, a -> a.put("messages.outbound.pending", outboundQueue.size()));
				messageDispatcher.send(connectionManager, outbound);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				// Exit loop
				break;
			} catch (Exception e) {
				// Don't really want this thread to exit, even if an exception does occur
				log.error("While processing outbound message", e);
			}
		}
	}

	private Message deserialize(byte[] in) {
		try {
			byte[] uncompressed = Snappy.uncompress(in);
			return serialization.fromDson(uncompressed, Message.class);
		} catch (IOException e) {
			throw new UncheckedIOException("While deserializing message", e);
		}
	}

	private void closeWithLog(TransportListener tl) {
		try {
			tl.close();
		} catch (IOException e) {
			log.error("Error closing transport listener " + tl, e);
		}
	}
}
