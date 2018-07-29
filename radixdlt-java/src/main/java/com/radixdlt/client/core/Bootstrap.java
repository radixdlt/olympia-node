package com.radixdlt.client.core;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.address.RadixUniverseConfigs;
import com.radixdlt.client.core.network.PeerDiscovery;
import com.radixdlt.client.core.network.PeersFromNodeFinder;
import com.radixdlt.client.core.network.PeersFromSeed;
import com.radixdlt.client.core.network.RadixPeer;

public enum Bootstrap implements BootstrapConfig {
	ALPHANET(
		RadixUniverseConfigs.getAlphanet(),
		new PeersFromNodeFinder("https://alphanet.radixdlt.com/node-finder", 443)
	),
	HIGHGARDEN(
		RadixUniverseConfigs.getHighgarden(),
		new PeersFromNodeFinder("https://highgarden.radixdlt.com/node-finder", 443)
	),
	SUNSTONE(
		RadixUniverseConfigs.getSunstone(),
		new PeersFromNodeFinder("https://sunstone.radixdlt.com/node-finder", 443)
	),
	WINTERFELL(
		RadixUniverseConfigs.getWinterfell(),
		new PeersFromSeed(new RadixPeer("52.190.0.18", false, 8080))
	),
	WINTERFELL_LOCAL(
		RadixUniverseConfigs.getWinterfell(),
		new PeersFromSeed(new RadixPeer("localhost", false, 8080))
	);

	private final RadixUniverseConfig config;
	private final PeerDiscovery discovery;

	Bootstrap(RadixUniverseConfig config, PeerDiscovery discovery) {
		this.config = config;
		this.discovery = discovery;
	}

	public RadixUniverseConfig getConfig() {
		return config;
	}

	public PeerDiscovery getDiscovery() {
		return discovery;
	}
}
