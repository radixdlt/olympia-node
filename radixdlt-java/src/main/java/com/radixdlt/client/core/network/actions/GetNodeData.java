package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.NodeRunnerData;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import java.util.Objects;

public class GetNodeData implements RadixNodeAction {
	public enum GetNodeDataType {
		GET_NODE_DATA_REQUEST,
		GET_NODE_DATA_RESULT,
	}

	private final GetNodeDataType type;
	private final RadixNode node;
	private final NodeRunnerData result;

	private GetNodeData(GetNodeDataType type, RadixNode node, NodeRunnerData result) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(node);

		this.type = type;
		this.node = node;
		this.result = result;
	}

	public GetNodeDataType getType() {
		 return type;
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	public NodeRunnerData getResult() {
		return result;
	}

	public static GetNodeData request(RadixNode node) {
		return new GetNodeData(GetNodeDataType.GET_NODE_DATA_REQUEST, node, null);
	}

	public static GetNodeData result(RadixNode node, NodeRunnerData result) {
		return new GetNodeData(GetNodeDataType.GET_NODE_DATA_RESULT, node, result);
	}

	@Override
	public String toString() {
		return type + " " + node + " " + result;
	}
}
