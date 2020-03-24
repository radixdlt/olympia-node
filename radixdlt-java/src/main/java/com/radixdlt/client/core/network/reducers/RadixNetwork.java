/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.network.reducers;

import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNodeState;
import com.radixdlt.client.core.network.actions.AddNodeAction;
import com.radixdlt.client.core.network.actions.GetNodeDataResultAction;
import com.radixdlt.client.core.network.actions.GetUniverseResponseAction;
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
			newMap = new HashMap<>(state.getNodeStates());
			newMap.merge(
				result.getNode(),
				RadixNodeState.of(action.getNode(), WebSocketStatus.CONNECTED, result.getResult()),
				(old, val) -> RadixNodeState.of(
					old.getNode(), old.getStatus(), val.getData().orElse(null), old.getUniverseConfig().orElse(null)
				)
			);
		} else if (action instanceof GetUniverseResponseAction) {
			final GetUniverseResponseAction getUniverseResponseAction = (GetUniverseResponseAction) action;
			final RadixNode node = action.getNode();
			newMap = new HashMap<>(state.getNodeStates());
			newMap.merge(
				node,
				RadixNodeState.of(node, WebSocketStatus.CONNECTED, null, getUniverseResponseAction.getResult()),
				(old, val) -> RadixNodeState.of(
					old.getNode(), old.getStatus(), old.getData().orElse(null), val.getUniverseConfig().orElse(null)
				)
			);
		} else if (action instanceof AddNodeAction) {
			final AddNodeAction addNodeAction = (AddNodeAction) action;
			final RadixNode node = action.getNode();
			newMap = new HashMap<>(state.getNodeStates());
			newMap.merge(
				node,
				RadixNodeState.of(node, WebSocketStatus.DISCONNECTED, addNodeAction.getData().orElse(null)),
				(old, val) -> RadixNodeState.of(old.getNode(), val.getStatus(),
					val.getData().orElse(old.getData().orElse(null)),
					old.getUniverseConfig().orElse(null)
				)
			);
		} else if (action instanceof WebSocketEvent) {
			final WebSocketEvent wsEvent = (WebSocketEvent) action;
			final RadixNode node = wsEvent.getNode();
			newMap = new HashMap<>(state.getNodeStates());
			newMap.merge(
				node,
				RadixNodeState.of(node, WebSocketStatus.valueOf(wsEvent.getType().name())),
				(old, val) -> RadixNodeState.of(
					old.getNode(), val.getStatus(), old.getData().orElse(null), old.getUniverseConfig().orElse(null)
				)
			);
		}


		return newMap != null ? new RadixNetworkState(newMap) : state;
	}
}
