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

package org.radix.common.executors;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public abstract class Executable implements Runnable
{
	private final long id = System.nanoTime();
	private final AtomicReference<Future<?>> future = new AtomicReference<>();

	private boolean terminated = false;
	private boolean finished = false;
	private CountDownLatch finishLatch;

	public final void terminate(boolean finish)
	{
		Future<?> thisFuture = this.future.get();
		if (thisFuture != null && !thisFuture.cancel(false) && finish) {
			finish();
		}
		this.terminated = true;
	}

	protected final boolean isTerminated()
	{
		return this.terminated;
	}

	protected final boolean isFinished()
	{
		return this.finished;
	}

	public abstract void execute();

	@Override
	public final void run()
	{
		try
		{
			if (this.terminated)
				throw new IllegalStateException("Executable "+this+" is terminated");

			this.finished = false;
			this.finishLatch = new CountDownLatch(1);
			execute();
		}
		catch (Throwable t)
		{
			Future<?> thisFuture = this.future.get();
			if (thisFuture != null) {
				thisFuture.cancel(false);
			}

			this.terminated = true;
		}
		finally
		{
			this.finished = true;
			this.finishLatch.countDown();
		}
	}

	public final long getID()
	{
		return this.id;
	}

	public final Future<?> getFuture()
	{
		return this.future.get();
	}

	final void setFuture(Future<?> future)
	{
		this.future.set(future);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (obj == null)
			return false;

		if (obj == this)
			return true;

		if (!(obj instanceof Executable))
			return false;

		return this.id == ((Executable)obj).id;
	}

	@Override
	public int hashCode()
	{
		return (int) (this.id & 0xFFFFFFFF);
	}

	@Override
	public String toString()
	{
		return "ID: "+this.id+" Terminated: "+this.terminated;
	}

	public void finish()
	{
		try {
			if (this.finishLatch != null)
				this.finishLatch.await();
		} catch (InterruptedException e) {
			// Re-raise
			Thread.currentThread().interrupt();
		}
	}
}
