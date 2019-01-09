package com.radixdlt.client.core.network.selector;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixNodeState;

import java.util.Objects;

/**
 * A universe filter that checks if peers have the same {@link RadixUniverseConfig}
 */
public class MatchingUniverseFilter implements RadixPeerFilter {
	private final RadixUniverseConfig universeConfig;

	public MatchingUniverseFilter(RadixUniverseConfig universeConfig) {
		Objects.requireNonNull(universeConfig, "universeConfig is required");

		this.universeConfig = universeConfig;
	}

	@Override
	public boolean test(RadixNodeState peerState) {
		return peerState.getUniverseConfig().map(universeConfig::equals).orElse(false);
	}
}
