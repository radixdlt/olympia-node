package org.radix.network2.messaging;

import java.util.Comparator;
import java.util.Map;

import org.radix.events.Event;
import org.radix.network.messages.PeerPingMessage;
import org.radix.network.messages.PeerPongMessage;
import org.radix.network.messaging.Message;
import org.radix.network.peers.Peer;

import com.google.common.collect.ImmutableMap;

/**
 * Inbound and outbound message wrapper with priority, time and destination.
 * <p>
 * Note that priority is calculated from a fixed table of priorities for
 * specific message types, and cannot be specified by the user.
 * <p>
 * Time is number of nanoseconds since some arbitrary baseline.
 */
public final class MessageEvent extends Event {

	private static final int DEFAULT_PRIORITY = 0;
	// Lower (inc -ve) numbers are higher priority than larger numbers
	private static final Map<Class<?>, Integer> MESSAGE_PRIORITIES = ImmutableMap.of(
		PeerPingMessage.class, Integer.MIN_VALUE,
		PeerPongMessage.class, Integer.MIN_VALUE
	);

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

	/**
	 * Returns the messages priority.
	 *
	 * @return the messages priority.
	 */
	public int priority() {
		return priority;
	}

	/**
	 * Returns the time this event was created as a number of nanoseconds
	 * since some arbitrary baseline.
	 *
	 * @return the time this event was created
	 */
	public long nanoTimeDiff() {
		return nanoTimeDiff;
	}

	/**
	 * Returns the source (for inbound) or destination (for outbound)
	 * of the message.
	 *
	 * @return the source or destination of the message.
	 */
	public Peer peer() {
		return peer;
	}

	/**
	 * Returns the message.
	 *
	 * @return the message.
	 */
	public Message message() {
		return message;
	}

	@Override
	public String toString() {
		return String.format("%s[priority=%s, nanoTime=%s, peer=%s, message=%s]",
			getClass().getSimpleName(), priority, nanoTimeDiff, peer, message);
	}
}