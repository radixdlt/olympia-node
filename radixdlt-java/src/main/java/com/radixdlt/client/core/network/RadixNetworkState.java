package com.radixdlt.client.core.network;

import com.google.common.collect.ImmutableMap;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable state in time of a {@link RadixNetwork}
 */
public class RadixNetworkState {
	public final Map<RadixPeer, WebSocketClient.RadixClientStatus> peers;

	public RadixNetworkState(Map<RadixPeer, WebSocketClient.RadixClientStatus> peers) {
		Objects.requireNonNull(peers, "peers is required");

		this.peers = Collections.unmodifiableMap(peers);
	}
}
