package com.radixdlt.client.core.network.reducers;

import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNodeState;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import com.radixdlt.client.core.network.actions.GetNodeData;
import com.radixdlt.client.core.network.actions.GetNodeData.GetNodeDataType;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import java.util.HashMap;
import java.util.Map;

public class RadixNetwork {
	public RadixNetwork() {
	}

	public RadixNetworkState reduce(RadixNetworkState state, RadixNodeAction action) {

		Map<RadixNode, RadixNodeState> newMap = null;
		if (action instanceof GetNodeData) {
			final GetNodeData getNodeData = (GetNodeData) action;
			if (getNodeData.getType().equals(GetNodeDataType.GET_NODE_DATA_RESULT)) {
				newMap = new HashMap<>(state.getNodes());
				newMap.merge(
					getNodeData.getNode(),
					RadixNodeState.of(action.getNode(), WebSocketStatus.CONNECTED, getNodeData.getResult()),
					(old, val) -> RadixNodeState.of(old.getNode(), old.getStatus(), val.getData().orElse(null))
				);
				return new RadixNetworkState(newMap);
			}
		} else if (action instanceof NodeUpdate) {
			final NodeUpdate nodeUpdate = (NodeUpdate) action;
			final RadixNode node = nodeUpdate.getNode();
			switch (nodeUpdate.getType()) {
				case ADD_NODE:
					newMap = new HashMap<>(state.getNodes());
					newMap.put(
						nodeUpdate.getNode(),
						RadixNodeState.of(nodeUpdate.getNode(), WebSocketStatus.DISCONNECTED, nodeUpdate.getData())
					);
					break;
				case DISCONNECTED:
				case CONNECTING:
				case CONNECTED:
				case CLOSING:
				case FAILED:
					newMap = new HashMap<>(state.getNodes());
					newMap.merge(
						node,
						RadixNodeState.of(node, WebSocketStatus.valueOf(nodeUpdate.getType().name())),
						(old, val) -> RadixNodeState.of(old.getNode(), val.getStatus(), old.getData().orElse(null))
					);
					break;
				default:
					break;
			}

		}

		return newMap != null ? new RadixNetworkState(newMap) : null;
	}
}
