/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.address.RadixUniverseConfigs;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.epics.DiscoverNodesEpic;
import io.reactivex.Observable;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public enum Bootstrap implements BootstrapConfig {
	LOCALHOST(
		new BootstrapByTrustedNode(
			ImmutableSet.of(
				new RadixNode("localhost", false, 8080),
				new RadixNode("localhost", false, 8081)
			)
		)
	),
	LOCALHOST_SINGLENODE(
		new BootstrapByTrustedNode(new RadixNode("localhost", false, 8080))
	),
	JENKINS(
		new BootstrapByTrustedNode(
			ImmutableSet.of(
				new RadixNode("docker_core0_1", false, 8080),
				new RadixNode("docker_core1_1", false, 8080)
			)
		)
	),
	BETANET(
		new BootstrapByTrustedNode(new RadixNode("betanet.radixdlt.com", true, 443))
	),
	BETANET_STATIC(
		RadixUniverseConfigs::getBetanet,
		new RadixNode("betanet.radixdlt.com", true, 443)
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

	Bootstrap(BootstrapConfig proxy) {
		this.config = proxy::getConfig;
		this.discoveryEpics = proxy::getDiscoveryEpics;
		this.initialNetwork = ImmutableSet.copyOf(proxy.getInitialNetwork());
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
