package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import java.util.Objects;

/**
 * The result of a {@link FindANodeRequestAction}.
 */
public class FindANodeResultAction implements RadixNodeAction {
	private final RadixNode selectedNode;
	private final FindANodeRequestAction request;

	public FindANodeResultAction(RadixNode selectedNode, FindANodeRequestAction request) {
		Objects.requireNonNull(selectedNode);
		Objects.requireNonNull(request);

		this.selectedNode = selectedNode;
		this.request = request;
	}

	/**
	 * The found/selected node
	 * @return the found/selected node
	 */
	@Override
	public RadixNode getNode() {
		return selectedNode;
	}

	/**
	 * The original request
	 * @return the original request
	 */
	public FindANodeRequestAction getRequest() {
		return request;
	}

	@Override
	public String toString() {
		return "FIND_A_NODE_RESULT " + selectedNode + " " + request;
	}
}
