package com.radixdlt.client.core.network;

import com.radixdlt.client.core.address.RadixUniverseConfig;

/**
 * Immutable state in time of a {@link RadixPeer}
 */
public class RadixPeerState {
	/**
	 * Location of {@link RadixPeer}
	 */
	public final String location;

	/**
	 * Port of {@link RadixPeer}
	 */
	public final int port;

	/**
	 * Status of {@link RadixPeer}'s client
	 */
	public final RadixClientStatus status;

	/**
	 * Node runner data of {@link RadixPeer}, may be null
	 */
	public final NodeRunnerData data;

	/**
	 * API Version of {@link RadixPeer}'s client, may be null
	 */
	public final Integer version;

	/**
	 * Universe configuration of {@link RadixPeer}'s client, may be null
	 */
	public final RadixUniverseConfig universeConfig;

	public RadixPeerState(String location, int port, RadixClientStatus status, NodeRunnerData data, Integer version, RadixUniverseConfig universeConfig) {
		this.location = location;
		this.port = port;
		this.status = status;
		this.data = data;
		this.version = version;
		this.universeConfig = universeConfig;
	}

	/**
	 * Create an immutable peer state from the current state of a {@link RadixPeer}
	 * @param peer The peer
	 * @return The immutable current state
	 */
	public static RadixPeerState from(RadixPeer peer) {
		RadixJsonRpcClient client = peer.getRadixClient();
		return new RadixPeerState(peer.getLocation(), peer.getPort(), client.getStatus().getValue(),
				peer.getData().getValue(), client.apiVersion().getValue(), client.universe().getValue());
	}

	@Override
	public String toString() {
		return "RadixPeerState{" +
				"location='" + location + '\'' +
				", port=" + port +
				", status=" + status +
				", data=" + data +
				", version=" + version +
				", universeConfig=" + universeConfig +
				'}';
	}
}
