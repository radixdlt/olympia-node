package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;

public class FindANodeResultAction implements RadixNodeAction {
	private final RadixNode selectedNode;
	private final FindANodeRequestAction request;

	public FindANodeResultAction(RadixNode selectedNode, FindANodeRequestAction request) {
		this.selectedNode = selectedNode;
		this.request = request;
	}

	@Override
	public RadixNode getNode() {
		return selectedNode;
	}

	public FindANodeRequestAction getRequest() {
		return request;
	}
}
