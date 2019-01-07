package com.radixdlt.client.core.network.reducers;

import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNodeState;
import com.radixdlt.client.core.network.actions.WebSocketEvent;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import com.radixdlt.client.core.network.actions.GetNodeDataAction;
import com.radixdlt.client.core.network.actions.GetNodeDataAction.GetNodeDataActionType;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import java.util.HashMap;
import java.util.Map;

/**
 * Reducer which controls state transitions as new network actions occur
 */
public class RadixNetwork {
	public RadixNetwork() {
	}

	public RadixNetworkState reduce(RadixNetworkState state, RadixNodeAction action) {
		Map<RadixNode, RadixNodeState> newMap = null;
		if (action instanceof GetNodeDataAction) {
			final GetNodeDataAction getNodeData = (GetNodeDataAction) action;
			if (getNodeData.getType().equals(GetNodeDataActionType.GET_NODE_DATA_RESULT)) {
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
			switch (nodeUpdate.getType()) {
				case ADD_NODE:
					newMap = new HashMap<>(state.getNodes());
					newMap.put(
						nodeUpdate.getNode(),
						RadixNodeState.of(nodeUpdate.getNode(), WebSocketStatus.DISCONNECTED, nodeUpdate.getData())
					);
					break;
				default:
					break;
			}
		} else if (action instanceof WebSocketEvent) {
			final WebSocketEvent wsEvent = (WebSocketEvent) action;
			final RadixNode node = wsEvent.getNode();
			newMap = new HashMap<>(state.getNodes());
			newMap.merge(
				node,
				RadixNodeState.of(node, WebSocketStatus.valueOf(wsEvent.getType().name())),
				(old, val) -> RadixNodeState.of(old.getNode(), val.getStatus(), old.getData().orElse(null))
			);
	}

	return newMap != null ? new RadixNetworkState(newMap) : null;
	}
}
