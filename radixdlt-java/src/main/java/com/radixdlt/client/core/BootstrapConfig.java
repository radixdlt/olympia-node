package com.radixdlt.client.core;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import java.util.List;

public interface BootstrapConfig {
	RadixUniverseConfig getConfig();
	List<RadixNetworkEpic> getDiscoveryEpics();
}
