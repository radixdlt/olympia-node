package org.radix.network2.transport.tcp;

import org.radix.properties.RuntimeProperties;

/**
 * Static configuration data for UDP transport configuration.
 */
public interface TCPConfiguration {

	/**
	 * Get the network address to bind listeners to.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return network address to bind listeners to
	 */
	String networkAddress(String defaultValue);

	/**
	 * Get the network port to bind listeners to.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return network port to bind listeners to
	 */
	int networkPort(int defaultValue);

	/**
	 * Get the number of processing threads to use for messages.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return the number threads for processing messages
	 */
	int processingThreads(int defaultValue);

	/**
	 * Get the priority of this transport.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return the priority of this transport
	 */
	int priority(int defaultValue);

	/**
	 * Create a configuration from specified {@link RuntimeProperties}.
	 *
	 * @param properties the properties to read the configuration from
	 * @return The configuration
	 */
	static TCPConfiguration fromRuntimeProperties(RuntimeProperties properties) {
		return new TCPConfiguration() {
			@Override
			public String networkAddress(String defaultValue) {
				return properties.get("network.tcp.address", defaultValue);
			}

			@Override
			public int networkPort(int defaultValue) {
				return properties.get("network.tcp.port", defaultValue);
			}

			@Override
			public int processingThreads(int defaultValue) {
				return properties.get("network.tcp.threads", defaultValue);
			}

			@Override
			public int priority(int defaultValue) {
				return properties.get("network.tcp.priority", defaultValue);
			}
		};
	}

}
