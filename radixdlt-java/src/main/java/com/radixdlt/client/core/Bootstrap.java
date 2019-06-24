package com.radixdlt.client.core;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.address.RadixUniverseConfigs;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.bootstrap.NodeFinder;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.epics.DiscoverNodesEpic;
import io.reactivex.Observable;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public enum Bootstrap implements BootstrapConfig {
	LOCALHOST(
		RadixUniverseConfigs::getLocalnet,
		new RadixNode("localhost", false, 8080),
		new RadixNode("localhost", false, 8081)
	),
	LOCALHOST_SINGLENODE(
		RadixUniverseConfigs::getLocalnet,
		new RadixNode("localhost", false, 8080)
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
	BETANET(
		RadixUniverseConfigs::getBetanet,
		new NodeFinder("https://betanet-staging.radixdlt.com/node-finder", 443).getSeed().toObservable()
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
	private final Supplier<List<RadixNetworkEpic>> discoveryEpics;
	private final ImmutableSet<RadixNode> initialNetwork;

	Bootstrap(Supplier<RadixUniverseConfig> config, Observable<RadixNode> seeds) {
		this.config = config;
		this.discoveryEpics = () -> Collections.singletonList(new DiscoverNodesEpic(seeds, config.get()));
		this.initialNetwork = ImmutableSet.of();
	}

	Bootstrap(Supplier<RadixUniverseConfig> config, RadixNode node, RadixNode... nodes) {
		this.config = config;
		this.discoveryEpics = Collections::emptyList;
		this.initialNetwork = new ImmutableSet.Builder<RadixNode>().add(node).add(nodes).build();
	}


	@Override
	public RadixUniverseConfig getConfig() {
		return config.get();
	}

	@Override
	public List<RadixNetworkEpic> getDiscoveryEpics() {
		return discoveryEpics.get();
	}

	@Override
	public Set<RadixNode> getInitialNetwork() {
		return initialNetwork;
	}
}
