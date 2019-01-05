package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.NodeRunnerData;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;

public class NodeUpdate implements RadixNodeAction {
	public enum NodeUpdateType {
		ADD_NODE,
		START_CONNECT,
		ADD_NODE_DATA,

		// Special for mini epic
		// TODO: remove this
		SELECT_NODE
	}

	private final RadixNode node;
	private final NodeUpdateType type;
	private final NodeRunnerData data;

	public NodeUpdate(NodeUpdateType type, RadixNode node, NodeRunnerData data) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(node);

		this.type = type;
		this.node = node;
		this.data = data;
	}

	public static NodeUpdate select(RadixNode node) {
		return new NodeUpdate(NodeUpdateType.SELECT_NODE, node, null);
	}

	public static NodeUpdate startConnect(RadixNode node) {
		return new NodeUpdate(NodeUpdateType.START_CONNECT, node, null);
	}

	public static NodeUpdate add(RadixNode node) {
		return new NodeUpdate(NodeUpdateType.ADD_NODE, node, null);
	}

	public static NodeUpdate nodeData(RadixNode node, NodeRunnerData data) {
		return new NodeUpdate(NodeUpdateType.ADD_NODE_DATA, node, data);
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	public NodeUpdateType getType() {
		return type;
	}

	public String toString() {
		return type + " " + node + " " + data;
	}
}
