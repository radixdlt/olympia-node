package com.radixdlt.client.core.network.reducers;

import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNodeState;
import com.radixdlt.client.core.network.actions.AddNodeAction;
import com.radixdlt.client.core.network.actions.GetNodeDataResultAction;
import com.radixdlt.client.core.network.actions.WebSocketEvent;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import java.util.HashMap;
import java.util.Map;

/**
 * Reducer which controls state transitions as new network actions occur.
 * Explicitly does not contain state and should be maintained as a pure function.
 */
public final class RadixNetwork {
	public RadixNetwork() {
	}

	public RadixNetworkState reduce(RadixNetworkState state, RadixNodeAction action) {
		Map<RadixNode, RadixNodeState> newMap = null;
		if (action instanceof GetNodeDataResultAction) {
			GetNodeDataResultAction result = (GetNodeDataResultAction) action;
			newMap = new HashMap<>(state.getNodes());
			newMap.merge(
				result.getNode(),
				RadixNodeState.of(action.getNode(), WebSocketStatus.CONNECTED, result.getResult()),
				(old, val) -> RadixNodeState.of(old.getNode(), old.getStatus(), val.getData().orElse(null))
			);
			return new RadixNetworkState(newMap);
		} else if (action instanceof AddNodeAction) {
			final AddNodeAction addNodeAction = (AddNodeAction) action;
			newMap = new HashMap<>(state.getNodes());
			newMap.put(
				addNodeAction.getNode(),
				RadixNodeState.of(addNodeAction.getNode(), WebSocketStatus.DISCONNECTED, addNodeAction.getData().orElse(null))
			);
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
