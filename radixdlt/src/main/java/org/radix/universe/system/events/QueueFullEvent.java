package org.radix.universe.system.events;

import java.util.Queue;

public final class QueueFullEvent extends SystemEvent
{
	private final Queue<?> queue;
	
	public QueueFullEvent(Queue<?> queue)
	{
		super();
		
		this.queue = queue;
	}
	
	public Queue<?> getQueue()
	{
		return this.queue;
	}
}
