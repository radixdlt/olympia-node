package com.radixdlt.client.core.network.actions;

import com.google.common.collect.ImmutableList;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.jsonrpc.NodeRunnerData;
import java.util.List;
import java.util.Objects;

/**
 * A dispatchable action result from a get live peers request
 */
public final class GetLivePeersResultAction implements JsonRpcResultAction<List<NodeRunnerData>> {
	private final RadixNode node;
	private final List<NodeRunnerData> data;

	private GetLivePeersResultAction(RadixNode node, List<NodeRunnerData> data) {
		Objects.requireNonNull(node);
		Objects.requireNonNull(data);

		this.node = node;
		this.data = ImmutableList.copyOf(data);
	}

	public static GetLivePeersResultAction of(RadixNode node, List<NodeRunnerData> data) {
		return new GetLivePeersResultAction(node, data);
	}

	@Override
	public List<NodeRunnerData> getResult() {
		return data;
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	@Override
	public String toString() {
		return "GET_LIVE_PEERS_RESULT " + node + " " + data.size() + " peers";
	}
}
