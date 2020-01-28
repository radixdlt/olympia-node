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
import java.util.Objects;

/**
 * A dispatchable action request for the node data of a given node
 */
public final class GetNodeDataRequestAction implements JsonRpcMethodAction {
	private final RadixNode node;

	private GetNodeDataRequestAction(RadixNode node) {
		Objects.requireNonNull(node);
		this.node = node;
	}

	public static GetNodeDataRequestAction of(RadixNode node) {
		return new GetNodeDataRequestAction(node);
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	@Override
	public String toString() {
		return "GET_NODE_DATA_REQUEST " + node;
	}
}
