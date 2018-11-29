package com.radixdlt.client.core.ledger.selector;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixPeer;
import com.radixdlt.client.core.network.WebSocketClient;

import java.util.Objects;

/**
 * A universe filter that checks if peers have the same {@link RadixUniverseConfig}
 */
public class UniverseFilter implements RadixPeerFilter {
	private final RadixUniverseConfig universeConfig;

	public UniverseFilter(RadixUniverseConfig universeConfig) {
		Objects.requireNonNull(universeConfig, "universeConfig is required");

		this.universeConfig = universeConfig;
	}

	@Override
	public boolean test(RadixPeer radixPeer, WebSocketClient.RadixClientStatus radixClientStatus) {
		return radixPeer.getRadixClient().getUniverse()
				.map(config -> config.equals(universeConfig))
				.onErrorReturnItem(false)
				.blockingGet();
	}
}
