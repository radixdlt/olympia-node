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
