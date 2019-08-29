package org.radix.network2.addressbook;

import org.radix.properties.RuntimeProperties;

/**
 * Static configuration data for {@link PeerManager}.
 */
public interface PeerManagerConfiguration {

	/**
	 * Returns the interval in seconds for which to ask a random peer for its
	 * list of peers.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return the interval in seconds for which to ask a random peer for its list of peers
	 */
	int networkPeersBroadcastInterval(int defaultValue);

	/**
	 * Returns the interval in seconds for which to probe known peers to determine
	 * availability.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return the interval in seconds for which to probe known peers
	 */
	int networkPeersProbeInterval(int defaultValue);

	/**
	 * Returns the minimum interval in seconds for which to probe an individual peer.
	 *
	 * @param defaultValue a default value if no special configuration value is set
	 * @return the minimum interval in seconds for which to probe an individual peer
	 */
	int networkPeerProbeDelay(int defaultValue);

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
			public int networkPeersProbeInterval(int defaultValue) {
				return properties.get("network.peers.probe.interval", defaultValue);
			}

			@Override
			public int networkPeerProbeDelay(int defaultValue) {
				return properties.get("network.peer.probe.delay", defaultValue);
			}
		};
	}
}
