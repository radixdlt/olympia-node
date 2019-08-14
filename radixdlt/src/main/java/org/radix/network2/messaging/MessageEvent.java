package org.radix.network2.messaging;

import java.util.Comparator;
import java.util.Map;

import org.radix.events.Event;
import org.radix.network.messages.PeerPingMessage;
import org.radix.network.messaging.Message;
import org.radix.network.peers.Peer;

import com.google.common.collect.ImmutableMap;

public final class MessageEvent extends Event {

	private static final int DEFAULT_PRIORITY = 0;
	// Lower (inc -ve) numbers are higher priority than larger numbers
	private static final Map<Class<?>, Integer> MESSAGE_PRIORITIES = ImmutableMap.of(PeerPingMessage.class, Integer.MIN_VALUE);

	static final Comparator<MessageEvent> COMPARATOR =
		Comparator.comparingInt(MessageEvent::priority).thenComparingLong(MessageEvent::nanoTimeDiff);

	private final int priority;
	private final long nanoTimeDiff;
	private final Peer peer;
	private final Message message;

	MessageEvent(Peer peer, Message message, long nanoTimeDiff) {
		super();

		this.priority = MESSAGE_PRIORITIES.getOrDefault(message.getClass(), DEFAULT_PRIORITY);
		this.nanoTimeDiff = nanoTimeDiff;
		this.peer = peer;
		this.message = message;
	}

	public int priority() {
		return priority;
	}

	public long nanoTimeDiff() {
		return nanoTimeDiff;
	}

	public Peer peer() {
		return peer;
	}

	public Message message() {
		return message;
	}

	@Override
	public String toString() {
		return String.format("%s[priority=%s, nanoTime=%s, peer=%s, message=%s]",
			getClass().getSimpleName(), priority, nanoTimeDiff, peer, message);
	}
}