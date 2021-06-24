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

package com.radixdlt.network.p2p;

import com.google.common.collect.ImmutableList;
import com.radixdlt.properties.RuntimeProperties;

import java.util.Arrays;

/**
 * Static configuration data for P2P layer.
 */
public interface P2PConfig {
	/**
	 * List of seed nodes for discovery.
	 */
	ImmutableList<String> seedNodes();

	/**
	 * Default port to use for discovery seed nodes.
	 */
	int defaultPort();

	/**
	 * An interval at which peer discovery rounds trigger.
	 */
	long discoveryInterval();

	/**
	 * Get the host to bind the p2p server to.
	 */
	String listenAddress();

	/**
	 * Get the port number to bind the p2p server to.
	 */
	int listenPort();

	/**
	 * Get node's port number to broadcast to other peers.
	 */
	int broadcastPort();

	/**
	 * The timeout for initiating outbound peer connection.
	 */
	int peerConnectionTimeout();

	/**
	 * Get the maximum number of inbound open channels allowed.
	 * Note that each channel consumes some resources on the host
	 * machine, and there may be other global operating-system defined limits
	 * that come into play.
	 */
	int maxInboundChannels();

	/**
	 * Get the maximum number of outbound open channels allowed.
	 * Note that each channel consumes some resources on the host
	 * machine, and there may be other global operating-system defined limits
	 * that come into play.
	 */
	int maxOutboundChannels();

	/**
	 * Get the buffer size of incoming messages for each TCP connection.
	 *
	 * @return the size of a message buffer
	 */
	int channelBufferSize();

	/**
	 * An interval at which peer liveness check is triggered (ping message).
	 */
	long peerLivenessCheckInterval();

	/**
	 * The timeout for receiving a Pong message. PeerLivenessLost event is triggered if pong is not received on time.
	 */
	long pingTimeout();

	/**
	 * Create a configuration from specified {@link RuntimeProperties}.
	 *
	 * @param properties the properties to read the configuration from
	 * @return The configuration
	 */
	static P2PConfig fromRuntimeProperties(RuntimeProperties properties) {
		return new P2PConfig() {
			@Override
			public ImmutableList<String> seedNodes() {
				return Arrays.stream(properties.get("network.p2p.seed_nodes", "").split(","))
					.map(String::trim)
					.filter(hn -> !hn.isEmpty())
					.collect(ImmutableList.toImmutableList());
			}

			@Override
			public int defaultPort() {
				return properties.get("network.p2p.default_port", 30000);
			}

			@Override
			public long discoveryInterval() {
				return properties.get("network.p2p.discovery_interval", 30_000);
			}

			@Override
			public String listenAddress() {
				return properties.get("network.p2p.listen_address", "0.0.0.0");
			}

			@Override
			public int listenPort() {
				return properties.get("network.p2p.listen_port", 30000);
			}

			@Override
			public int broadcastPort() {
				return properties.get("network.p2p.broadcast_port", listenPort());
			}

			@Override
			public int peerConnectionTimeout() {
				return properties.get("network.p2p.peer_connection_timeout", 5000);
			}

			@Override
			public int maxInboundChannels() {
				return properties.get("network.p2p.max_inbound_channels", 1024);
			}

			@Override
			public int maxOutboundChannels() {
				return properties.get("network.p2p.max_outbound_channels", 1024);
			}

			@Override
			public int channelBufferSize() {
				return properties.get("network.p2p.channel_buffer_size", 255);
			}

			@Override
			public long peerLivenessCheckInterval() {
				return properties.get("network.p2p.peer_liveness_check_interval", 10000);
			}

			@Override
			public long pingTimeout() {
				return properties.get("network.p2p.ping_timeout", 5000);
			}
		};
	}
}
