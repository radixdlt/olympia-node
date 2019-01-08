package com.radixdlt.client.core.network.selector;

import com.radixdlt.client.core.network.jsonrpc.NodeRunnerData;
import com.radixdlt.client.core.network.RadixNodeState;

import java.util.Objects;
import java.util.Set;

/**
 * A shard filter that tests if peers serve the given shard space
 */
public class ShardFilter implements RadixPeerFilter {
	private final Set<Long> shards;

	public ShardFilter(Set<Long> shards) {
		Objects.requireNonNull(shards, "shards is required");

		this.shards = shards;
	}

	@Override
	public boolean test(RadixNodeState nodeState) {
		return nodeState.getData().map(NodeRunnerData::getShards).map(s -> s.intersects(this.shards)).orElse(false);
	}
}
