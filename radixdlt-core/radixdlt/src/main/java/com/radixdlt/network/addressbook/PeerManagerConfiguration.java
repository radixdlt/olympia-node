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

package com.radixdlt.network.addressbook;

import com.radixdlt.properties.RuntimeProperties;

/**
 * Static configuration data for {@link PeerManager}.
 */
public interface PeerManagerConfiguration {

	/**
	 * Returns the interval in millisecond for which to ask a random peer for its
	 * list of peers.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return the interval in millisecond for which to ask a random peer for its list of peers
	 */
	int networkPeersBroadcastInterval(int defaultValue);

	/**
	 * Returns the delay in millisecond for peers broadcast message.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return delay in millisecond before first peer broadcast message
	 */
	int networkPeersBroadcastDelay(int defaultValue);

	/**
	 * Returns the interval in millisecond for which to probe known peers to determine
	 * availability.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return the interval in millisecond for which to probe known peers
	 */
	int networkPeersProbeInterval(int defaultValue);

	/**
	 * Returns the delay in millisecond before first probe message.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return delay in millisecond before first probe message
	 */
	int networkPeersProbeDelay(int defaultValue);

	/**
	 * Returns timeout in millisecond for probe handler.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return timeout in millisecond for probe handler.
	 */
	int networkPeersProbeTimeout(int defaultValue);

	/**
	 * Returns the minimum interval in millisecond for which to probe an individual peer.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return the minimum interval in millisecond for which to probe an individual peer
	 */
	int networkPeersProbeFrequency(int defaultValue);

	/**
	 * Returns the delay in millisecond for peers heartbeat message.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return delay in millisecond before first peer heartbeat message
	 */
	int networkHeartbeatPeersDelay(int defaultValue);

	/**
	 * Returns the delay in millisecond for peers heartbeat message.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return delay in millisecond between heartbeat messages
	 */
	int networkHeartbeatPeersInterval(int defaultValue);

	/**
	 * Returns the delay in millisecond for discover peers message.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return delay in millisecond between discover messages
	 */
	int networkDiscoverPeersInterval(int defaultValue);

	/**
	 * Returns the delay in millisecond before first discover peers message
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return delay in millisecond before first discover peers message
	 */
	int networkDiscoverPeersDelay(int defaultValue);

	/**
	 * Returns maximum number of peers in PeersMessage.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return maximum number of peers in PeersMessage
	 */
	int networkPeersMessageBatchSize(int defaultValue);

	/**
	 * Create a configuration from specified {@link RuntimeProperties}.
	 *
	 * @param properties the properties to read the configuration from
	 * @return The configuration
	 */
	static PeerManagerConfiguration fromRuntimeProperties(RuntimeProperties properties) {
		return new PeerManagerConfiguration() {
			@Override
			public int networkPeersBroadcastInterval(int defaultValue) {
				return properties.get("network.peers.broadcast.interval", defaultValue);
			}

			@Override
			public int networkPeersBroadcastDelay(int defaultValue) {
				return properties.get("network.peers.broadcast.delay", defaultValue);
			}

			@Override
			public int networkPeersProbeInterval(int defaultValue) {
				return properties.get("network.peers.probe.interval", defaultValue);
			}

			@Override
			public int networkPeersProbeDelay(int defaultValue) {
				return properties.get("network.peers.probe.delay", defaultValue);
			}

			@Override
			public int networkPeersProbeTimeout(int defaultValue) {
				return properties.get("network.peers.probe.timeout", defaultValue);
			}

			@Override
			public int networkPeersProbeFrequency(int defaultValue) {
				return properties.get("network.peers.probe.frequency", defaultValue);
			}

			@Override
			public int networkHeartbeatPeersInterval(int defaultValue) {
				return properties.get("network.peers.heartbeat.interval", defaultValue);
			}

			@Override
			public int networkHeartbeatPeersDelay(int defaultValue) {
				return properties.get("network.peers.heartbeat.delay", defaultValue);
			}

			@Override
			public int networkDiscoverPeersInterval(int defaultValue) {
				return properties.get("network.peers.discover.interval", defaultValue);
			}

			@Override
			public int networkDiscoverPeersDelay(int defaultValue) {
				return properties.get("network.peers.discover.delay", defaultValue);
			}

			@Override
			public int networkPeersMessageBatchSize(int defaultValue) {
				return properties.get("network.peers.message.batch.size", defaultValue);
			}
		};
	}
}
