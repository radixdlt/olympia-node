package com.radixdlt.client.core.network;

import com.radixdlt.client.core.address.RadixUniverseConfig;

import com.radixdlt.client.core.atoms.Shards;
import com.radixdlt.client.core.network.jsonrpc.NodeRunnerData;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable state at a certain point in time of a {@link RadixNode}
 */
public class RadixNodeState {
	private final RadixNode node;
	private final WebSocketStatus status;
	private final NodeRunnerData data;
	private final Integer version;
	private final RadixUniverseConfig universeConfig;

	public RadixNodeState(RadixNode node, WebSocketStatus status, NodeRunnerData data, Integer version,
	                      RadixUniverseConfig universeConfig) {
		Objects.requireNonNull(node, "node is required");
		Objects.requireNonNull(status, "status is required");

		this.node = node;
		this.status = status;
		this.data = data;
		this.version = version;
		this.universeConfig = universeConfig;
	}

	public static RadixNodeState of(RadixNode node, WebSocketStatus status) {
		return new RadixNodeState(node, status, null, null, null);
	}

	public static RadixNodeState of(RadixNode node, WebSocketStatus status, NodeRunnerData data) {
		return new RadixNodeState(node, status, data, null, null);
	}

	@Override
	public String toString() {
		return "RadixNodeState{"
				+ "node='" + node + '\''
				+ ", status=" + status
				+ ", data=" + data
				+ ", version=" + version
				+ ", universeConfig=" + universeConfig
				+ '}';
	}

	public RadixNode getNode() {
		return node;
	}

	/**
	 * Status of {@link RadixNode}'s client
	 */
	public WebSocketStatus getStatus() {
		return status;
	}

	public Optional<Shards> getShards() {
		return getData().map(NodeRunnerData::getShards);
	}

	/**
	 * Node runner data of {@link RadixNode}, may be null
	 */
	public Optional<NodeRunnerData> getData() {
		return Optional.ofNullable(this.data);
	}

	/**
	 * API Version of {@link RadixNode}'s client, may be null
	 */
	public Optional<Integer> getVersion() {
		return Optional.ofNullable(this.version);
	}

	/**
	 * Universe configuration of {@link RadixNode}'s client, may be null
	 */
	public Optional<RadixUniverseConfig> getUniverseConfig() {
		return Optional.ofNullable(this.universeConfig);
	}
}
