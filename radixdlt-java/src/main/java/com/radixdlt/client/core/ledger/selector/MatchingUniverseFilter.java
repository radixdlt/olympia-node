package com.radixdlt.client.core.ledger.selector;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixPeerState;

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
	public boolean test(RadixPeerState peerState) {
		return peerState.universeConfig != null && peerState.universeConfig.equals(universeConfig);
	}
}
