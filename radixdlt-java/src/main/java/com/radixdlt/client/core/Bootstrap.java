package com.radixdlt.client.core;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.address.RadixUniverseConfigs;
import com.radixdlt.client.core.network.bootstrap.NodeFinder;
import com.radixdlt.client.core.network.RadixNode;
import io.reactivex.Observable;
import java.util.function.Supplier;

public enum Bootstrap implements BootstrapConfig {
	BETANET(
		RadixUniverseConfigs::getBetanet,
		Observable.just(new RadixNode("localhost", false, 8080))
	),
	ALPHANET(
		RadixUniverseConfigs::getAlphanet,
		new NodeFinder("https://alphanet.radixdlt.com/node-finder", 443).getSeed().toObservable()
	),
	HIGHGARDEN(
		RadixUniverseConfigs::getHighgarden,
		new NodeFinder("https://highgarden.radixdlt.com/node-finder", 443).getSeed().toObservable()
	),
	SUNSTONE(
		RadixUniverseConfigs::getSunstone,
		new NodeFinder("https://sunstone.radixdlt.com/node-finder", 443).getSeed().toObservable()
	),
	WINTERFELL(
		RadixUniverseConfigs::getWinterfell,
		Observable.just(new RadixNode("52.190.0.18", false, 8080))
	),
	WINTERFELL_LOCAL(
		RadixUniverseConfigs::getWinterfell,
		Observable.just(new RadixNode("localhost", false, 8080))
	);

	private final Supplier<RadixUniverseConfig> config;
	private final Observable<RadixNode> seeds;

	Bootstrap(Supplier<RadixUniverseConfig> config, Observable<RadixNode> seeds) {
		this.config = config;
		this.seeds = seeds;
	}

	public RadixUniverseConfig getConfig() {
		return config.get();
	}

	public Observable<RadixNode> getSeeds() {
		return seeds;
	}
}
