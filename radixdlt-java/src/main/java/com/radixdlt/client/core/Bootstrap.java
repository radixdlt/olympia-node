package com.radixdlt.client.core;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.address.RadixUniverseConfigs;
import com.radixdlt.client.core.network.NodeFinder;
import com.radixdlt.client.core.network.RadixPeer;
import io.reactivex.Observable;
import java.util.function.Supplier;

public enum Bootstrap implements BootstrapConfig {
	BETANET(
		RadixUniverseConfigs::getBetanet,
		Observable.just(new RadixPeer("localhost", false, 8080))
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
		Observable.just(new RadixPeer("52.190.0.18", false, 8080))
	),
	WINTERFELL_LOCAL(
		RadixUniverseConfigs::getWinterfell,
		Observable.just(new RadixPeer("localhost", false, 8080))
	);

	private final Supplier<RadixUniverseConfig> config;
	private final Observable<RadixPeer> seeds;

	Bootstrap(Supplier<RadixUniverseConfig> config, Observable<RadixPeer> seeds) {
		this.config = config;
		this.seeds = seeds;
	}

	public RadixUniverseConfig getConfig() {
		return config.get();
	}

	public Observable<RadixPeer> getSeeds() {
		return seeds;
	}
}
