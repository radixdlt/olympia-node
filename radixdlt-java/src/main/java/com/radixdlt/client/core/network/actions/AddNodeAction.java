package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.jsonrpc.NodeRunnerData;
import java.util.Objects;
import java.util.Optional;

/**
 * A dispatchable action to request to add a node to the network state.
 */
public final class AddNodeAction implements RadixNodeAction {
	private final RadixNode node;
	private final NodeRunnerData data;

	private AddNodeAction(RadixNode node, NodeRunnerData data) {
		Objects.requireNonNull(node);

		this.node = node;
		this.data = data;
	}

	public static AddNodeAction of(RadixNode node) {
		return new AddNodeAction(node, null);
	}

	public static AddNodeAction of(RadixNode node, NodeRunnerData data) {
		return new AddNodeAction(node, data);
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	public Optional<NodeRunnerData> getData() {
		return Optional.ofNullable(data);
	}

	public String toString() {
		return "ADD_NODE_ACTION " + node + " " + data;
	}
}
