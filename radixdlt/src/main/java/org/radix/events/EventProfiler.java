package org.radix.events;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.radix.common.executors.ScheduledExecutable;
import org.radix.modules.Module;
import org.radix.modules.Modules;
import org.radix.modules.Service;
import org.radix.modules.exceptions.ModuleException;
import org.radix.utils.SystemMetaData;

public class EventProfiler extends Service implements EventListener<Event>
{
	private Map<Long, Long>	eventProfiles = new ConcurrentHashMap<Long, Long>();

	@Override
	public void start_impl() throws ModuleException
	{
		Modules.ifAvailable(SystemMetaData.class, a -> a.put("events.processing", 0));

		scheduleWithFixedDelay(new ScheduledExecutable(10, 1, TimeUnit.SECONDS)
		{
			long event_load = 0;

			@Override
			public void execute()
			{
				long now = System.currentTimeMillis();
				event_load = 0;

				if (!eventProfiles.isEmpty())
				{
					Iterator<Long> eventProfilesIterator = eventProfiles.keySet().iterator();
					while (eventProfilesIterator.hasNext())
					{
						Long eventID = eventProfilesIterator.next();

						if (now-eventProfiles.get(eventID) > 10000)
						{
							eventProfilesIterator.remove();
							continue;
						}
					}

					if (!eventProfiles.isEmpty())
						event_load = eventProfiles.size()/10;
				}

				Modules.ifAvailable(SystemMetaData.class, a -> a.put("events.processing", event_load));
			}
		});
	}

	@Override
	public void stop_impl() throws ModuleException
	{ }

	@Override
	public void process(Event event)
	{
		eventProfiles.put(event.getNonce(), event.getTimestamp());
	}

	@Override
	public List<Class<? extends Module>> getDependsOn() {
		return Collections.singletonList(SystemMetaData.class);
	}
}
