package org.radix.events;

import org.radix.common.Syncronicity;
import org.radix.events.Event.EventPriority;

public interface EventListener<T extends Event>
{
	public void process(T event) throws Throwable;

	public default Syncronicity getSyncronicity()
	{
		return Syncronicity.ASYNCRONOUS;
	}
}
