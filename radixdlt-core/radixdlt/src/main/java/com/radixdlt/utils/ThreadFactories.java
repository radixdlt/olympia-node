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

package com.radixdlt.utils;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility methods for creating thread factories.
 */
public final class ThreadFactories {

	private ThreadFactories() {
		throw new IllegalStateException("Can't construct");
	}

	static class CountingThreadFactory implements ThreadFactory {
		private final String nameFormat;
		private final boolean daemon;
		private final AtomicLong counter = new AtomicLong(1L);

		CountingThreadFactory(String nameFormat, boolean daemon) {
			this.nameFormat = nameFormat;
			this.daemon = daemon;
		}

		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, String.format(nameFormat, counter.getAndIncrement()));
			t.setDaemon(this.daemon);
			return t;
		}

		@Override
		public String toString() {
			return String.format("%s[%s:%s]", getClass().getSimpleName(), this.daemon, this.nameFormat);
		}
	}

	/**
	 * Creates a {@link ThreadFactory} that uses the specified format string
	 * to create threads with identifiable names.  The threads created by the
	 * factory will be daemon threads.
	 *
	 * @param nameFormat a {@link String#format(String, Object...)}-compatible
	 *		format {@link String}, to which a unique integer (0, 1, etc.) will
	 *		be supplied as the single parameter. This integer will be unique
	 *		and assigned sequentially as each thread is constructed. For example,
	 *		{@code "rpc-pool-%d"} will generate thread names like
	 *		{@code "rpc-pool-0"}, {@code "rpc-pool-1"}, {@code "rpc-pool-2"},
	 *		etc.
	 * @return the newly constructed {@link ThreadFactory}
	 */
	public static ThreadFactory daemonThreads(String nameFormat) {
		return new CountingThreadFactory(nameFormat, true);
	}

	/**
	 * Creates a {@link ThreadFactory} that uses the specified format string
	 * to create threads with identifiable names.  The threads created by the
	 * factory will be non-daemon threads.
	 *
	 * @param nameFormat a {@link String#format(String, Object...)}-compatible
	 *		format {@link String}, to which a unique integer (0, 1, etc.) will
	 *		be supplied as the single parameter. This integer will be unique
	 *		and assigned sequentially as each thread is constructed. For example,
	 *		{@code "rpc-pool-%d"} will generate thread names like
	 *		{@code "rpc-pool-0"}, {@code "rpc-pool-1"}, {@code "rpc-pool-2"},
	 *		etc.
	 * @return the newly constructed {@link ThreadFactory}
	 */
	public static ThreadFactory threads(String nameFormat) {
		return new CountingThreadFactory(nameFormat, false);
	}
}
