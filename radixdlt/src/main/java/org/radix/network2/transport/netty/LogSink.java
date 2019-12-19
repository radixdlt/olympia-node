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
