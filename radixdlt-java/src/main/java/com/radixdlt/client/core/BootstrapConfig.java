package com.radixdlt.client.core;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.address.RadixUniverseConfigs;
import com.radixdlt.client.core.network.PeerDiscovery;
import com.radixdlt.client.core.network.PeersFromNodeFinder;
import com.radixdlt.client.core.network.PeersFromSeed;

public interface BootstrapConfig {
	RadixUniverseConfig getConfig();
	PeerDiscovery getDiscovery();
}
