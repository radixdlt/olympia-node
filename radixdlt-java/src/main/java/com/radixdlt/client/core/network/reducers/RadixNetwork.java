package com.radixdlt.client.core.network.reducers;

import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNodeStatus;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RadixNetwork {
	/**
	 * Hot observable which updates subscribers of new connection events
	 */
	private final BehaviorSubject<RadixNetworkState> networkState;


	public RadixNetwork() {
		this.networkState = BehaviorSubject.createDefault(new RadixNetworkState(Collections.emptyMap()));
	}

	/**
	 * Returns a cold observable of network state
	 *
	 * @return a cold observable of network state
	 */
	public Observable<RadixNetworkState> getNetworkState() {
		return this.networkState;
	}

	public void reduce(RadixNodeAction action) {
		if (action instanceof NodeUpdate) {
			final NodeUpdate nodeUpdate = (NodeUpdate) action;
			final RadixNode node = nodeUpdate.getNode();
			switch(nodeUpdate.getType()) {
				case ADD_NODE: {
					RadixNetworkState prev = networkState.getValue();
					Map<RadixNode, RadixNodeState> newMap = new HashMap<>(prev.getPeers());
					newMap.put(nodeUpdate.getNode(), RadixNodeState.of(action.getNode(), RadixNodeStatus.DISCONNECTED, nodeUpdate.getData()));
					networkState.onNext(new RadixNetworkState(newMap));
					break;
				}
				case DISCONNECTED:
				case CONNECTING:
				case CONNECTED:
				case CLOSING:
				case FAILED: {
					RadixNetworkState prev = networkState.getValue();
					Map<RadixNode, RadixNodeState> newMap = new HashMap<>(prev.getPeers());
					newMap.merge(
						node,
						RadixNodeState.of(node, RadixNodeStatus.valueOf(nodeUpdate.getType().name())),
						(old, val) -> RadixNodeState.of(old.getNode(), val.getStatus(), old.getData().orElse(null))
					);
					networkState.onNext(new RadixNetworkState(newMap));
					break;
				}
			}
		}
	}
}
