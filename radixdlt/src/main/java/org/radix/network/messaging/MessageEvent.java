package org.radix.network.messaging;

import java.util.Comparator;

import org.radix.events.Event;
import org.radix.network.peers.Peer;

public final class MessageEvent extends Event
{
	public static final Comparator<MessageEvent> COMPARATOR =
		Comparator.comparingInt(MessageEvent::getPriority).thenComparingLong(MessageEvent::getNanoTime);

	private final int priority;
	private final long nanoTime;
	private final Message	message;
	private final Peer		peer;

	public MessageEvent(int priority, long nanoTime, Message message, Peer peer)
	{
		super();

		this.priority = priority;
		this.nanoTime = nanoTime;
		this.message = message;
		this.peer = peer;
	}

	public int getPriority() {
		return priority;
	}

	public long getNanoTime() {
		return nanoTime;
	}

	public Message getMessage()
	{
		return message;
	}

	public Peer getPeer()
	{
		return peer;
	}
}