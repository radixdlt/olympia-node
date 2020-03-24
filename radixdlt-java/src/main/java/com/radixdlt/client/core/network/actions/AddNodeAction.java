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

import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.jsonrpc.NodeRunnerData;
import java.util.Objects;
import java.util.Optional;

/**
 * A dispatchable action to request to add a node to the network state.
 */
public final class AddNodeAction implements RadixNodeAction {
	private final RadixNode node;
	private final NodeRunnerData data;

	private AddNodeAction(RadixNode node, NodeRunnerData data) {
		Objects.requireNonNull(node);

		this.node = node;
		this.data = data;
	}

	public static AddNodeAction of(RadixNode node) {
		return new AddNodeAction(node, null);
	}

	public static AddNodeAction of(RadixNode node, NodeRunnerData data) {
		return new AddNodeAction(node, data);
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	public Optional<NodeRunnerData> getData() {
		return Optional.ofNullable(data);
	}

	@Override
	public String toString() {
		return "ADD_NODE_ACTION " + node + " " + data;
	}
}
