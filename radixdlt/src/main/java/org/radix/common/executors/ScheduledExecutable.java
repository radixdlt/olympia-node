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

import java.util.concurrent.TimeUnit;

public abstract class ScheduledExecutable extends Executable
{
	private final long initialDelay;
	private final long recurrentDelay;
	private final TimeUnit unit;

	public ScheduledExecutable(long initialDelay, long recurrentDelay, TimeUnit unit)
	{
		super();

		this.initialDelay = initialDelay;
		this.recurrentDelay = recurrentDelay;
		this.unit = unit;
	}

	public long getInitialDelay() { return this.initialDelay; }

	public long getRecurrentDelay() { return this.recurrentDelay; }

	public TimeUnit getTimeUnit() { return this.unit; }
}

