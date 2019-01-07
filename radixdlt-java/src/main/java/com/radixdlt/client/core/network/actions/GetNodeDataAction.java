package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.jsonrpc.NodeRunnerData;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;

public class GetNodeDataAction implements JsonRpcAction {
	public enum GetNodeDataActionType {
		GET_NODE_DATA_REQUEST(JsonRpcActionType.REQUEST),
		GET_NODE_DATA_RESULT(JsonRpcActionType.RESULT);

		private final JsonRpcActionType jsonRpcActionType;

		GetNodeDataActionType(JsonRpcActionType jsonRpcActionType) {
			this.jsonRpcActionType = jsonRpcActionType;
		}

		public JsonRpcActionType getJsonRpcActionType() {
			return jsonRpcActionType;
		}
	}

	private final GetNodeDataActionType type;
	private final RadixNode node;
	private final NodeRunnerData result;

	private GetNodeDataAction(GetNodeDataActionType type, RadixNode node, NodeRunnerData result) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(node);

		this.type = type;
		this.node = node;
		this.result = result;
	}

	public GetNodeDataActionType getType() {
		 return type;
	}

	@Override
	public JsonRpcActionType getJsonRpcActionType() {
		return type.getJsonRpcActionType();
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	public NodeRunnerData getResult() {
		return result;
	}

	public static GetNodeDataAction request(RadixNode node) {
		return new GetNodeDataAction(GetNodeDataActionType.GET_NODE_DATA_REQUEST, node, null);
	}

	public static GetNodeDataAction result(RadixNode node, NodeRunnerData result) {
		return new GetNodeDataAction(GetNodeDataActionType.GET_NODE_DATA_RESULT, node, result);
	}

	@Override
	public String toString() {
		return type + " " + node + " " + result;
	}
}
