package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import com.radixdlt.client.core.network.actions.NodeUpdate.NodeUpdateType;
import com.radixdlt.client.core.network.actions.WebSocketEvent;
import com.radixdlt.client.core.network.epics.WebSocketsEpic.WebSockets;
import io.reactivex.Observable;

public class WebSocketEventsEpic implements RadixNetworkEpic {
	private final WebSockets webSockets;

	public WebSocketEventsEpic(WebSockets webSockets) {
		this.webSockets = webSockets;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		return
			actions
				.filter(a -> a instanceof NodeUpdate)
				.map(NodeUpdate.class::cast)
				.filter(u -> u.getType().equals(NodeUpdateType.ADD_NODE))
				.flatMap(u ->
					webSockets.get(u.getNode())
						.getState()
						.map(s -> WebSocketEvent.nodeStatus(u.getNode(), s))
				);
	}
}
