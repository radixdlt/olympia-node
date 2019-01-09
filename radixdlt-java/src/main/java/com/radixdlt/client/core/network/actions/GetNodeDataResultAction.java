package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.jsonrpc.NodeRunnerData;
import java.util.Objects;

/**
 * A dispatchable action response for a given node data request
 */
public final class GetNodeDataResultAction implements JsonRpcResultAction<NodeRunnerData> {
	private final RadixNode node;
	private final NodeRunnerData data;

	private GetNodeDataResultAction(RadixNode node, NodeRunnerData data) {
		Objects.requireNonNull(node);
		Objects.requireNonNull(data);

		this.node = node;
		this.data = data;
	}

	public static GetNodeDataResultAction of(RadixNode node, NodeRunnerData data) {
		return new GetNodeDataResultAction(node, data);
	}

	@Override
	public NodeRunnerData getResult() {
		return data;
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	public String toString() {
		return "GET_NODE_DATA_RESPONSE " + node + " " + data;
	}
}
