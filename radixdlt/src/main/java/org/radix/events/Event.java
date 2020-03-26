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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.radix.common.Syncronicity;

public abstract class Event implements Comparable<Event>, Future<Event>
{
	private static AtomicLong noncer = new AtomicLong(1);

	private final long 	nonce;
	private final long	timestamp;
	private final CountDownLatch latch;
	private boolean done;

	public Event()
	{
		this.nonce = noncer.incrementAndGet();
		this.timestamp = System.currentTimeMillis();
		this.done = false;
		this.latch = new CountDownLatch(1);
	}

	public boolean supportedSyncronicity(Syncronicity syncronicity)
	{
		return true;
	}

	public long getTimestamp()
	{
		return this.timestamp;
	}

	long getNonce()
	{
		return this.nonce;
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning)
	{
		// Events can not be cancelled, always return false
		return false;
	}

	@Override
	public Event get() throws InterruptedException, ExecutionException
	{
		this.latch.await();
		return this;
	}

	@Override
	public Event get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
	{
		if (this.latch.await(timeout, unit) == true)
			return this;

		return null;
	}

	@Override
	public boolean isCancelled()
	{
		// Events can not be cancelled, always return false
		return false;
	}

	@Override
	public boolean isDone()
	{
		return this.done;
	}

	void setDone()
	{
		this.latch.countDown();
		this.done = true;
	}

	@Override
	public int hashCode()
	{
		return (int) (31l*getClass().hashCode()*timestamp*nonce);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (obj == null) return false;
		if (obj == this) return true;

		if (obj instanceof Event && ((Event)obj).nonce == this.nonce)
			return true;

		return false;
	}

	@Override
	public int compareTo(Event event)
	{
		if (this.timestamp < event.timestamp)
			return -1;

		if (this.timestamp > event.timestamp)
			return 1;

		return 0;
	}

	@Override
	public String toString()
	{
		return getClass().getSimpleName()+" "+this.timestamp;
	}
}
