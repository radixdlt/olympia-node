package org.radix.network2.messaging;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network.messaging.Message;
import org.radix.network2.NetworkLegacyPatching;
import org.radix.network2.TimeSupplier;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.transport.Transport;
import org.radix.universe.system.events.QueueFullEvent;
import org.radix.utils.SystemMetaData;
import org.xerial.snappy.Snappy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.Inject;
import com.radixdlt.serialization.Serialization;

//FIXME: Optional dependency on Modules.get(SystemMetadata.class) for updating metadata
final class MessageCentralImpl implements MessageCentral {
	private static final Logger log = Logging.getLogger("message");

	private static final MessageListenerList EMPTY_MESSAGE_LISTENER_LIST = new MessageListenerList();

	// Dependencies
	private final Serialization serialization;
	private final TransportManager connectionManager;
	private final Events events;

	// Local data

	// Message dispatching
	private final MessageDispatcher messageDispatcher;

	// Listeners
	private final ConcurrentHashMap<Class<? extends Message>, MessageListenerList> listeners = new ConcurrentHashMap<>();

	// Our time base for System.nanoTime() differences.  Per documentation can only compare deltas
	private final long timeBase = System.nanoTime();

	// Listeners we are managing
	private final List<Transport> transports;

	// Limit rate at which dropped packet logs are produced
	private final RateLimiter inboundLogRateLimiter = RateLimiter.create(1.0);
	private final RateLimiter outboundLogRateLimiter = RateLimiter.create(1.0);

	// Inbound message handling
	private final BlockingQueue<MessageEvent> inboundQueue;
	private final SimpleThreadPool<MessageEvent> inboundThreadPool;

	// Outbound message handling
	private final BlockingQueue<MessageEvent> outboundQueue;
	private final SimpleThreadPool<MessageEvent> outboundThreadPool;


	@Inject
	public MessageCentralImpl(
		MessageCentralConfiguration config,
		Serialization serialization,
		TransportManager transportManager,
		Events events,
		TimeSupplier timeSource,
		EventQueueFactory<MessageEvent> eventQueueFactory
	) {
		this.inboundQueue = eventQueueFactory.createEventQueue(config.messagingInboundQueueMax(8192));
		this.outboundQueue = eventQueueFactory.createEventQueue(config.messagingOutboundQueueMax(16384));

		this.serialization = Objects.requireNonNull(serialization);
		this.connectionManager = Objects.requireNonNull(transportManager);
		this.events = Objects.requireNonNull(events);

		Objects.requireNonNull(timeSource);
		this.messageDispatcher = new MessageDispatcher(config, serialization, timeSource);

		this.transports = Lists.newArrayList(transportManager.transports());

		// Start inbound processing thread
		int inboundThreads = config.messagingInboundQueueThreads(1);
		this.inboundThreadPool = new SimpleThreadPool<>("Inbound message processing", inboundThreads, inboundQueue::take, this::inboundMessageProcessor);
		this.inboundThreadPool.start();

		// Start outbound processing thread
		int outboundThreads = config.messagingOutboundQueueThreads(1);
		this.outboundThreadPool = new SimpleThreadPool<>("Outbound message processing", outboundThreads, outboundQueue::take, this::outboundMessageProcessor);
		this.outboundThreadPool.start();

		// Start our listeners
		this.transports.forEach(tl -> tl.start(this::inboundMessage));
	}

	@Override
	public void close() {
		this.transports.forEach(tl -> closeWithLog(tl));
		this.transports.clear();

		inboundThreadPool.stop();
		outboundThreadPool.stop();
	}

	@Override
	public void send(Peer peer, Message message) {
		if (!outboundQueue.offer(new MessageEvent(peer, null, message, System.nanoTime() - timeBase))) {
			if (outboundLogRateLimiter.tryAcquire()) {
				log.error(String.format("Outbound message to %s dropped", peer));
			}
			events.broadcast(new QueueFullEvent());
		}
	}

	@Override
	public void inject(Peer peer, Message message) {
		MessageEvent event = new MessageEvent(peer, null, message, System.nanoTime() - timeBase);
		if (!inboundQueue.offer(event)) {
			if (inboundLogRateLimiter.tryAcquire()) {
				log.error(String.format("Injected message from %s dropped", peer));
			}
			events.broadcast(new QueueFullEvent());
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
	int listenersSize() {
		return listeners.values().stream().mapToInt(MessageListenerList::size).sum();
	}

	private void inboundMessage(InboundMessage inboundMessage) {
		Peer peer = NetworkLegacyPatching.findPeer(inboundMessage.source());
		if (peer != null) {
			Message message = deserialize(inboundMessage.message());
			inject(peer, message);
		}
	}

	private void inboundMessageProcessor(MessageEvent inbound) {
		Modules.ifAvailable(SystemMetaData.class, a -> a.put("messages.inbound.pending", inboundQueue.size()));
		MessageListenerList listeners = this.listeners.getOrDefault(inbound.message().getClass(), EMPTY_MESSAGE_LISTENER_LIST);
		messageDispatcher.receive(listeners, inbound);
	}

	private void outboundMessageProcessor(MessageEvent outbound) {
		Modules.ifAvailable(SystemMetaData.class, a -> a.put("messages.outbound.pending", outboundQueue.size()));
		messageDispatcher.send(connectionManager, outbound);
	}

	private Message deserialize(byte[] in) {
		try {
			byte[] uncompressed = Snappy.uncompress(in);
			return serialization.fromDson(uncompressed, Message.class);
		} catch (IOException e) {
			throw new UncheckedIOException("While deserializing message", e);
		}
	}

	private void closeWithLog(Transport t) {
		try {
			t.close();
		} catch (IOException e) {
			log.error("Error closing transport " + t, e);
		}
	}
}
