package com.radixdlt.client.core;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.PeerDiscovery;

public interface BootstrapConfig {
	RadixUniverseConfig getConfig();
	PeerDiscovery getDiscovery();
}
