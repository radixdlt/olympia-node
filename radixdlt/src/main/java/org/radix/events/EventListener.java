package org.radix.events;

import org.radix.common.Syncronicity;
import org.radix.events.Event.EventPriority;
import org.radix.interfaces.Listener;

public interface EventListener<T extends Event> extends Listener, Comparable<EventListener<?>>
{
	public void process(T event) throws Throwable;

	public default int getPriority()
	{
		return EventPriority.DEFAULT.priority();
	}

	public default Syncronicity getSyncronicity()
	{
		return Syncronicity.ASYNCRONOUS;
	}

	@Override
	default int compareTo(EventListener<?> other)
	{
		if (this.getPriority() > other.getPriority())
			return -1;

		if (this.getPriority() < other.getPriority())
			return 1;

		return 0;
	}
}
