package com.radixdlt.client.core.network.reducers;

import com.radixdlt.client.core.address.RadixUniverseConfig;

import com.radixdlt.client.core.network.NodeRunnerData;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeStatus;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable state at a certain point in time of a {@link RadixNode}
 */
public class RadixNodeState {
	private final RadixNode node;
	private final RadixNodeStatus status;
	private final NodeRunnerData data;
	private final Integer version;
	private final RadixUniverseConfig universeConfig;

	public RadixNodeState(RadixNode node, RadixNodeStatus status, NodeRunnerData data, Integer version,
	                      RadixUniverseConfig universeConfig) {
		Objects.requireNonNull(node, "node is required");
		Objects.requireNonNull(status, "status is required");

		this.node = node;
		this.status = status;
		this.data = data;
		this.version = version;
		this.universeConfig = universeConfig;
	}

	public static RadixNodeState of(RadixNode node, RadixNodeStatus status) {
		return new RadixNodeState(node, status, null, null, null);
	}

	public static RadixNodeState of(RadixNode node, RadixNodeStatus status, NodeRunnerData data) {
		return new RadixNodeState(node, status, data, null, null);
	}

	@Override
	public String toString() {
		return "RadixPeerState{"
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
	public RadixNodeStatus getStatus() {
		return status;
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
