package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.NodeRunnerData;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class GetLivePeers implements RadixNodeAction {
	public enum GetLivePeersType {
		GET_LIVE_PEERS_REQUEST,
		GET_LIVE_PEERS_RESULT,
	}

	private final GetLivePeersType type;
	private final RadixNode node;
	private final List<NodeRunnerData> result;

	private GetLivePeers(GetLivePeersType type, RadixNode node, List<NodeRunnerData> result) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(node);

		this.type = type;
		this.node = node;
		this.result = result == null ? null : Collections.unmodifiableList(new ArrayList<>(result));
	}

	public GetLivePeersType getType() {
		return type;
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	public List<NodeRunnerData> getResult() {
		return result;
	}

	public static GetLivePeers request(RadixNode node) {
		return new GetLivePeers(GetLivePeersType.GET_LIVE_PEERS_REQUEST, node, null);
	}

	public static GetLivePeers result(RadixNode node, List<NodeRunnerData> result) {
		return new GetLivePeers(GetLivePeersType.GET_LIVE_PEERS_RESULT, node, result);
	}

	@Override
	public String toString() {
		return type + " " + node + " " + result;
	}
}
