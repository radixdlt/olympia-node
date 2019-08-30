package org.radix.network.messaging;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.radixdlt.common.EUID;
import org.radix.common.executors.ScheduledExecutable;
import org.radix.modules.Modules;
import org.radix.modules.Service;
import org.radix.modules.exceptions.ModuleException;
import org.radix.network2.addressbook.Peer;
import org.radix.utils.SystemMetaData;

public class MessageProfiler extends Service
{
	private Map<EUID, Long>	messages = new ConcurrentHashMap<>();

	@Override
	public void start_impl() throws ModuleException
	{
		Modules.ifAvailable(SystemMetaData.class, a -> a.put("messages.processed", 0));
		Modules.ifAvailable(SystemMetaData.class, a -> a.put("messages.processing", 0));

		scheduleWithFixedDelay(new ScheduledExecutable(10, 1, TimeUnit.SECONDS)
		{
			long message_load = 0;

			@Override
			public void execute()
			{
				long now = System.currentTimeMillis();
				message_load = 0;

				if (!messages.isEmpty())
				{
					Iterator<EUID> messagesIterator = messages.keySet().iterator();
					while (messagesIterator.hasNext())
					{
						EUID messageID = messagesIterator.next();

						if (now-messages.get(messageID) > 10000)
						{
							messagesIterator.remove();
							continue;
						}
					}

					if (!messages.isEmpty())
						message_load = messages.size()/10;
				}

				Modules.ifAvailable(SystemMetaData.class, a -> a.put("messages.processing", message_load));
			}
		});
	}

	@Override
	public void stop_impl() throws ModuleException
	{ }

	public void process(Message m, Peer peer)
	{
		Modules.ifAvailable(SystemMetaData.class, a -> a.increment("messages.processed"));
		messages.put(m.getHID(), System.currentTimeMillis());
	}
}
