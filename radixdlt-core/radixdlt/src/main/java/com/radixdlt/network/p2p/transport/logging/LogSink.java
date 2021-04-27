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

package com.radixdlt.network.p2p.transport.logging;

import org.apache.logging.log4j.Logger;

/**
 * A sink that accepts log strings.
 */
public interface LogSink {

	/**
	 * Returns {@code true} if trace logs enabled.
	 *
	 * return {@code true} if trace logs enabled.
	 */
	boolean isTraceEnabled();

	/**
	 * Outputs the specified log message at trace level.
	 *
	 * @param message The message to output.
	 */
	void trace(String message);

	/**
	 * Outputs the specified log message and exception at trace level.
	 *
	 * @param message The message to output.
	 * @param ex The exception to include.
	 */
	void trace(String message, Throwable ex);

	/**
	 * Create a {@link LogSink} using the specified logger.
	 *
	 * @param log The logger that will consume log messages
	 * @return A newly constructed {@link LogSink}
	 */
	static LogSink using(Logger log) {
		return new LogSink() {

			@Override
			public boolean isTraceEnabled() {
				return log.isTraceEnabled();
			}

			@Override
			public void trace(String message) {
				log.trace(message);
			}

			@Override
			public void trace(String message, Throwable ex) {
				log.trace(message, ex);
			}

		};
	}

}
