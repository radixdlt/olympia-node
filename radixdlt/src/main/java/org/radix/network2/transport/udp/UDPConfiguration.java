package org.radix.network2.transport.udp;

import org.radix.properties.RuntimeProperties;

/**
 * Static configuration data for UDP transport configuration.
 */
public interface UDPConfiguration {

	/**
	 * Get the network address to bind listeners to.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return network address to bind listeners to
	 */
	String getNetworkAddress(String defaultValue);

	/**
	 * Get the network port to bind listeners to.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return network port to bind listeners to
	 */
	int getNetworkPort(int defaultValue);

	/**
	 * Get the number of processing threads to use for inbound messages.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return the number threads for processing inbound messages
	 */
	int getInboundProcessingThreads(int defaultValue);

	/**
	 * Create a configuration from specified {@link RuntimeProperties}.
	 *
	 * @param properties the properties to read the configuration from
	 * @return The configuration
	 */
	static UDPConfiguration fromRuntimeProperties(RuntimeProperties properties) {
		return new UDPConfiguration() {
			@Override
			public String getNetworkAddress(String defaultValue) {
				return properties.get("network.udp.address", defaultValue);
			}

			@Override
			public int getNetworkPort(int defaultValue) {
				return properties.get("network.udp.port", defaultValue);
			}

			@Override
			public int getInboundProcessingThreads(int defaultValue) {
				return properties.get("network.udp.threads", defaultValue);
			}
		};
	}

}
