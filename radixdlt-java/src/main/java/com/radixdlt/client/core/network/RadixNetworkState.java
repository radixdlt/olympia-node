package com.radixdlt.client.core.network;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Current state in time of a {@link RadixNetwork}
 */
public class RadixNetworkState {
	public final Map<RadixPeer, RadixPeerState> peers;

	public RadixNetworkState(Map<RadixPeer, RadixPeerState> peers) {
		Objects.requireNonNull(peers, "peers is required");

		this.peers = Collections.unmodifiableMap(peers);
	}
}
