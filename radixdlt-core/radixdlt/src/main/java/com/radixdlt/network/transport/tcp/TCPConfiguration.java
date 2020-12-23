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

package com.radixdlt.network.transport.tcp;

import com.radixdlt.properties.RuntimeProperties;

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
	 * Get the maximum number of open channels allowed, both inbound and
	 * outbound.  Note that each channel consumes some resources on the host
	 * machine, and there may be other global operating-system defined limits
	 * that come into play.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return the maximum number of open TCP channels at any one time
	 */
	int maxChannelCount(int defaultValue);

	/**
	 * Get the priority of this transport.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return the priority of this transport
	 * @see org.radix.network2.transport.Transport#priority()
	 */
	int priority(int defaultValue);

	/**
	 * Provide hexdump of sent and received data.
	 * Note that both "trace" logging level, and this flag will
	 * need to be enabled to get hexdumps in log output.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return {@code true} if hexdump data is required, {@code false} otherwise
	 */
	boolean debugData(boolean defaultValue);

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
			public int maxChannelCount(int defaultValue) {
				return properties.get("network.tcp.maxchannels", defaultValue);
			}

			@Override
			public int priority(int defaultValue) {
				return properties.get("network.tcp.priority", defaultValue);
			}

			@Override
			public boolean debugData(boolean defaultValue) {
				return properties.get("network.tcp.debug_data", defaultValue);
			}
		};
	}

}
