package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.NodeRunnerData;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixPeer;
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

	private final RadixPeer node;
	private final NodeUpdateType type;
	private final NodeRunnerData data;

	public NodeUpdate(NodeUpdateType type, RadixPeer node, NodeRunnerData data) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(node);

		this.type = type;
		this.node = node;
		this.data = data;
	}

	public static NodeUpdate select(RadixPeer node) {
		return new NodeUpdate(NodeUpdateType.SELECT_NODE, node, null);
	}

	public static NodeUpdate startConnect(RadixPeer node) {
		return new NodeUpdate(NodeUpdateType.START_CONNECT, node, null);
	}

	public static NodeUpdate add(RadixPeer node) {
		return new NodeUpdate(NodeUpdateType.ADD_NODE, node, null);
	}

	public static NodeUpdate nodeData(RadixPeer node, NodeRunnerData data) {
		return new NodeUpdate(NodeUpdateType.ADD_NODE_DATA, node, data);
	}

	@Override
	public RadixPeer getNode() {
		return node;
	}

	public NodeUpdateType getType() {
		return type;
	}

	public String toString() {
		return type + " " + node + " " + data;
	}
}
