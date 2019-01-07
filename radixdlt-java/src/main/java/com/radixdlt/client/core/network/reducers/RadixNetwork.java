package com.radixdlt.client.core.network.reducers;

import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNodeStatus;
import com.radixdlt.client.core.network.actions.GetNodeData;
import com.radixdlt.client.core.network.actions.GetNodeData.GetNodeDataType;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import java.util.HashMap;
import java.util.Map;

public class RadixNetwork {
	public RadixNetwork() {
	}

	public RadixNetworkState reduce(RadixNetworkState state, RadixNodeAction action) {
		if (action instanceof GetNodeData) {
			final GetNodeData getNodeData = (GetNodeData) action;
			if (getNodeData.getType().equals(GetNodeDataType.GET_NODE_DATA_RESULT)) {
				Map<RadixNode, RadixNodeState> newMap = new HashMap<>(state.getPeers());
				newMap.merge(
					getNodeData.getNode(),
					RadixNodeState.of(action.getNode(), RadixNodeStatus.CONNECTED, getNodeData.getResult()),
					(old, val) -> RadixNodeState.of(old.getNode(), old.getStatus(), val.getData().orElse(null))
				);
				return new RadixNetworkState(newMap);
			}
		} else if (action instanceof NodeUpdate) {
			final NodeUpdate nodeUpdate = (NodeUpdate) action;
			final RadixNode node = nodeUpdate.getNode();
			switch(nodeUpdate.getType()) {
				case ADD_NODE: {
					Map<RadixNode, RadixNodeState> newMap = new HashMap<>(state.getPeers());
					newMap.put(nodeUpdate.getNode(), RadixNodeState.of(nodeUpdate.getNode(), RadixNodeStatus.DISCONNECTED, nodeUpdate.getData()));
					return new RadixNetworkState(newMap);
				}
				case DISCONNECTED:
				case CONNECTING:
				case CONNECTED:
				case CLOSING:
				case FAILED: {
					Map<RadixNode, RadixNodeState> newMap = new HashMap<>(state.getPeers());
					newMap.merge(
						node,
						RadixNodeState.of(node, RadixNodeStatus.valueOf(nodeUpdate.getType().name())),
						(old, val) -> RadixNodeState.of(old.getNode(), val.getStatus(), old.getData().orElse(null))
					);
					return new RadixNetworkState(newMap);
				}
			}
		}

		return null;
	}
}
