package com.radixdlt.client.core;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixNode;
import io.reactivex.Observable;

public interface BootstrapConfig {
	RadixUniverseConfig getConfig();
	Observable<RadixNode> getSeeds();
}
