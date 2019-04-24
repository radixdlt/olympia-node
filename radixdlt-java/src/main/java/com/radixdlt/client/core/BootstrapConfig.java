package com.radixdlt.client.core;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNode;
import java.util.List;
import java.util.Set;

public interface BootstrapConfig {
	RadixUniverseConfig getConfig();
	List<RadixNetworkEpic> getDiscoveryEpics();
	Set<RadixNode> getInitialNetwork();
}
