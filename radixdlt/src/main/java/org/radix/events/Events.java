/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.radix.common.Syncronicity;
import org.radix.common.executors.Executable;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.utils.SystemMetaData;

import com.google.common.collect.Maps;

public final class Events
{
	private static final Events instance;

	static
	{
		instance = new Events();
	}

	public static final Events getInstance()
	{
		return Events.instance;
	}

	private static final Logger eventLog = Logging.getLogger("events");

	private class EventListeners
	{
		private final CopyOnWriteArrayList<EventListener<?>> syncronousListeners = new CopyOnWriteArrayList<>();
		private final CopyOnWriteArrayList<EventListener<?>> asyncronousListeners = new CopyOnWriteArrayList<>();

		List<EventListener<?>> get(Syncronicity syncronicity)
		{
			if (syncronicity.equals(Syncronicity.SYNCRONOUS))
				return this.syncronousListeners;
			else if (syncronicity.equals(Syncronicity.ASYNCRONOUS))
				return this.asyncronousListeners;

			throw new IllegalArgumentException("Syncronicity "+syncronicity+" not supported");
		}

		boolean has(Syncronicity syncronicity)
		{
			if (syncronicity.equals(Syncronicity.SYNCRONOUS))
				return this.syncronousListeners.isEmpty() == false;
			else if (syncronicity.equals(Syncronicity.ASYNCRONOUS))
				return this.asyncronousListeners.isEmpty() == false;

			throw new IllegalArgumentException("Syncronicity "+syncronicity+" not supported");
		}

		void add(EventListener<?> listener) {
			Syncronicity synchronicity = listener.getSyncronicity();
			if (synchronicity.equals(Syncronicity.ASYNCRONOUS)) {
				this.asyncronousListeners.addIfAbsent(listener);
			} else  if (synchronicity.equals(Syncronicity.SYNCRONOUS)) {
				this.syncronousListeners.addIfAbsent(listener);
			} else {
				throw new IllegalArgumentException("Synchronicity " + synchronicity + " not supported");
			}
		}

		void remove(EventListener<?> listener) {
			Syncronicity synchronicity = listener.getSyncronicity();
			if (synchronicity.equals(Syncronicity.ASYNCRONOUS)) {
				this.asyncronousListeners.remove(listener);
			} else  if (synchronicity.equals(Syncronicity.SYNCRONOUS)) {
				this.syncronousListeners.remove(listener);
			} else {
				throw new IllegalArgumentException("Synchronicity " + synchronicity + " not supported");
			}
		}
	}

	private class ExecutorThreadFactory implements ThreadFactory
	{
		private int nextID = 0;
		private final Set<Thread> threads = new HashSet<>();
		public final AtomicInteger cycle = new AtomicInteger(0);

		@Override
		public Thread newThread(Runnable runnable)
		{
			Thread thread = new Thread(runnable, "Event Thread "+(nextID++));
			thread.setDaemon(true);
			this.threads.add(thread);
			return thread;
		}
	}

	private class EventQueueProcessor extends Executable
	{
		private final Object 				mutex = new Object();
		private final Queue<Event>			eventQueue;
		private final List<EventListeners>	listeners;

		public EventQueueProcessor()
		{
			this.eventQueue = new ConcurrentLinkedQueue<>();
			this.listeners = new ArrayList<>();
		}

		public boolean offer(Event event)
		{
			boolean inserted = this.eventQueue.offer(event);

			synchronized(this.mutex)
			{
				this.mutex.notifyAll();
			}

			return inserted;
		}

		@Override
		public void execute()
		{
			while (isTerminated() == false)
			{
				synchronized(this.mutex)
				{
					if (eventQueue.peek() == null)
					{
						try {
							this.mutex.wait(1000);
						} catch (InterruptedException e) {
							// Just exit if we are interrupted
							Thread.currentThread().interrupt();
							break;
						}
						continue;
					}
				}

		        if (eventQueue.peek() == null)
		        	continue;

		        int drain = eventQueue.size();
		        while(eventQueue.peek() != null && drain-- > 0)
		        {
					Event event = eventQueue.poll();

					try
					{
						if (event.isDone())
							continue;

						long start = System.nanoTime();

						try
						{
							this.listeners.clear();
							synchronized(Events.this.listeners)
							{
								for (Class<? extends Event> type : Events.this.listeners.keySet())
								{
									if (type.isAssignableFrom(event.getClass()) == false)
										continue;

									this.listeners.add(Events.this.listeners.get(type));
								}
							}

							// TODO want to optionally isolate any Exceptions thrown on SYNCRONOUS events
							for (EventListeners listeners : this.listeners)
							{
								for (EventListener<?> listener : listeners.get(Syncronicity.ASYNCRONOUS))
								{
									try
									{
										((org.radix.events.EventListener<Event>)listener).process(event);
									}
									catch (Throwable t)
									{
										eventLog.error(t.getMessage(), t);
									}
								}
							}

							event.setDone();

							SystemMetaData.ifPresent( a -> {
								a.increment("events.dequeued");
								a.increment("events.processed.asynchronous");
							});
						}
						finally
						{
							long duration = System.nanoTime() - start;
							if (TimeUnit.NANOSECONDS.toMicros(duration) > 100_000)
								eventLog.debug("Asynchronous processing of "+event.getClass()+" took "+TimeUnit.NANOSECONDS.toMicros(duration)+" micros");
						}
					}
					catch (Exception ex)
					{
						eventLog.error("Processing of "+event.getClass()+" failed", ex);
					}
		        }
			}
		}
	}

	private final ExecutorThreadFactory		executorThreadFactory = new ExecutorThreadFactory();
	  // TODO have EventHandler threads configurable in RuntimeProperties?
	private final ExecutorService			executor = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors()/2), this.executorThreadFactory);
	private EventQueueProcessor[] 			eventQueueProcessors = new EventQueueProcessor[Math.max(1, Runtime.getRuntime().availableProcessors()/2)];

	private final Set<Thread> reentrancy = new HashSet<>();
	private final Map<Class<? extends Event>, EventListeners> listeners = new HashMap<> ();

	Events()
	{
		for (int i = 0 ; i < eventQueueProcessors.length ; i++)
		{
			eventQueueProcessors[i] = new EventQueueProcessor();
			this.executor.submit(eventQueueProcessors[i]);
		}
	}

	// TODO convert to overridden Module.getMetaData
	public Map<String, Object> getMetaData()
	{
		final Map<String, Object> metaData = Maps.newHashMap();
		SystemMetaData.ifPresent( a -> {
			final Map<String, Object> processedMetaData = Maps.newHashMap();
			processedMetaData.put("synchronous", a.get("events.processed.synchronous", 0L));
			processedMetaData.put("asynchronous", a.get("events.processed.asynchronous", 0L));
			metaData.put("processed",  processedMetaData);

			metaData.put("processing",  a.get("events.processing", 0L));
			metaData.put("broadcast",  a.get("events.broadcast", 0L));
			metaData.put("queued", a.get("events.queued", 0L));
			metaData.put("dequeued",  a.get("events.dequeued", 0L));
		});
		return metaData;
	}

	public void broadcast(final Event event)
	{
		try
		{
			broadcastWithException(event);
		}
		catch (Throwable t)
		{
			eventLog.error(event.getClass()+" caused exception that is uncaught", t);
		}
	}

	public void broadcastWithException(final Event event) throws Throwable
	{
		SystemMetaData.ifPresent( a -> a.increment("events.broadcast"));

		synchronized(this.reentrancy)
		{
			if (this.reentrancy.contains(Thread.currentThread()))
				eventLog.warn("Reentrancy on EventHandler for thread "+Thread.currentThread().toString()+" (perhaps nested SYNCRONOUS broadcasts)");
		}

		try
		{
			boolean hasAsync = false;
			boolean hasSync = false;
			synchronized(this.listeners)
			{
				for (Class<? extends Event> type : this.listeners.keySet())
				{
					if (type.isAssignableFrom(event.getClass()) == false)
						continue;

					if (hasSync == false)
						hasSync = this.listeners.get(type).has(Syncronicity.SYNCRONOUS);

					if (hasAsync == false)
						hasAsync = this.listeners.get(type).has(Syncronicity.ASYNCRONOUS);

					if (hasAsync == true && hasSync == true)
						break;
				}
			}

			if (hasSync == true && event.supportedSyncronicity(Syncronicity.SYNCRONOUS))
			{
				synchronized(this.reentrancy)
				{
					this.reentrancy.add(Thread.currentThread());
				}

				long syncronousStart = System.nanoTime();

				try
				{
					synchronized(this.listeners)
					{
						for (Class<? extends Event> type : this.listeners.keySet())
						{
							if (type.isAssignableFrom(event.getClass()) == false)
								continue;

							// TODO want to optionally isolate any Exceptions thrown on SYNCRONOUS events
							for (EventListener<?> listener : this.listeners.get(type).get(Syncronicity.SYNCRONOUS))
								((org.radix.events.EventListener<Event>)listener).process(event);
						}
					}

					SystemMetaData.ifPresent( a -> a.increment("events.processed.synchronous"));
				}
				finally
				{
					if (TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - syncronousStart) > 10000)
						eventLog.warn("Synchronous processing of "+event.getClass()+" took "+TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - syncronousStart)+" micros");
				}
			}

			if (hasAsync == true)
			{
				this.eventQueueProcessors[Events.this.executorThreadFactory.cycle.incrementAndGet() % this.eventQueueProcessors.length].offer(event);
				SystemMetaData.ifPresent( a -> a.increment("events.queued"));
			}
			else
				event.setDone();
		}
		finally
		{
			synchronized(this.reentrancy)
			{
				this.reentrancy.remove(Thread.currentThread());
			}
		}
	}

	public void register(final Class<? extends Event> type, final EventListener<? extends Event> listener)
	{
		synchronized(this.listeners) {
			this.listeners.computeIfAbsent(type, k -> new EventListeners()).add(listener);
		}
	}

	public void deregister(final Class<? extends Event> type, final EventListener<? extends Event> listener)
	{
		synchronized(this.listeners) {
			this.listeners.computeIfAbsent(type, k -> new EventListeners()).remove(listener);
		}
	}
}
