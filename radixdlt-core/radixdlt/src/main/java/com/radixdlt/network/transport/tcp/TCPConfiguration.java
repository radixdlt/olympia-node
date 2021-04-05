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
	int listenPort(int defaultValue);

	/**
	 * Get the network port to broadcast to other peers.
	 * This can be different from the listen port if proxy server is used.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return network port to broadcast to other peers
	 */
	int broadcastPort(int defaultValue);

	/**
	 * Get the maximum number of inbound open channels allowed.
	 * Note that each channel consumes some resources on the host
	 * machine, and there may be other global operating-system defined limits
	 * that come into play.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return the maximum number of open inbound TCP channels at any one time
	 */
	int maxInChannelCount(int defaultValue);

	/**
	 * Get the maximum number of outbound open channels allowed.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return the maximum number of open outbound TCP channels at any one time
	 */
	int maxOutChannelCount(int defaultValue);

	/**
	 * Get the priority of this transport.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return the priority of this transport
	 * @see com.radixdlt.network.transport.Transport#priority()
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
	 * Get the buffer size of incoming messages for each TCP connection.
	 *
	 * @return the size of a message buffer
	 */
	int messageBufferSize(int defaultValue);

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
			public int listenPort(int defaultValue) {
				return properties.get("network.tcp.listen_port", defaultValue);
			}

			@Override
			public int broadcastPort(int defaultValue) {
				return properties.get("network.tcp.broadcast_port", defaultValue);
			}

			@Override
			public int maxInChannelCount(int defaultValue) {
				return properties.get("network.tcp.max_in_channels", defaultValue);
			}

			@Override
			public int maxOutChannelCount(int defaultValue) {
				return properties.get("network.tcp.max_out_channels", defaultValue);
			}

			@Override
			public int priority(int defaultValue) {
				return properties.get("network.tcp.priority", defaultValue);
			}

			@Override
			public boolean debugData(boolean defaultValue) {
				return properties.get("network.tcp.debug_data", defaultValue);
			}

			@Override
			public int messageBufferSize(int defaultValue) {
				return properties.get("network.tcp.message_buffer_size", defaultValue);
			}
		};
	}

}
