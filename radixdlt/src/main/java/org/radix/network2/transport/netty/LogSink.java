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

package org.radix.network2.transport.netty;

import org.radix.logging.Logger;

/**
 * A sink that accepts log strings.
 */
public interface LogSink {

	/**
	 * Outputs the specified log message.
	 *
	 * @param message The message to output.
	 */
	void log(String message);

	/**
	 * Outputs the specified log message and exception.
	 *
	 * @param message The message to output.
	 * @param ex The exception to include.
	 */
	void log(String message, Throwable ex);

	/**
	 * Create a {@link LogSink} for the specified logger at the debug level.
	 *
	 * @param log The logger that will consume log messages
	 * @return A newly constructed {@link LogSink}
	 */
	static LogSink forDebug(Logger log) {
		return new LogSink() {

			@Override
			public void log(String message, Throwable ex) {
				log.debug(message, ex);
			}

			@Override
			public void log(String message) {
				log.debug(message);
			}
		};
	}

}
