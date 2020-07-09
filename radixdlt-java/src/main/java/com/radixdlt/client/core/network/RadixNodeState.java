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

package com.radixdlt.client.core.network;

import com.radixdlt.client.core.address.RadixUniverseConfig;

import com.radixdlt.client.core.network.jsonrpc.NodeRunnerData;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable state at a certain point in time of a {@link RadixNode}
 */
public class RadixNodeState {
	private final RadixNode node;
	private final WebSocketStatus status;
	private final NodeRunnerData data;
	private final Integer version;
	private final RadixUniverseConfig universeConfig;

	public RadixNodeState(RadixNode node, WebSocketStatus status, NodeRunnerData data, Integer version,
	                      RadixUniverseConfig universeConfig) {
		Objects.requireNonNull(node, "node is required");
		Objects.requireNonNull(status, "status is required");

		this.node = node;
		this.status = status;
		this.data = data;
		this.version = version;
		this.universeConfig = universeConfig;
	}

	public static RadixNodeState of(RadixNode node, WebSocketStatus status) {
		return new RadixNodeState(node, status, null, null, null);
	}

	public static RadixNodeState of(RadixNode node, WebSocketStatus status, NodeRunnerData data) {
		return new RadixNodeState(node, status, data, null, null);
	}

	public static RadixNodeState of(RadixNode node, WebSocketStatus status, NodeRunnerData data, RadixUniverseConfig universeConfig) {
		return new RadixNodeState(node, status, data, null, universeConfig);
	}

	@Override
	public String toString() {
		return "RadixNodeState{"
				+ "node='" + node + '\''
				+ ", status=" + status
				+ ", data=" + data
				+ ", universeConfig=" + universeConfig
				+ '}';
	}

	public RadixNode getNode() {
		return node;
	}

	/**
	 * Status of {@link RadixNode}'s client
	 */
	public WebSocketStatus getStatus() {
		return status;
	}

	/**
	 * Node runner data of {@link RadixNode}
	 */
	public Optional<NodeRunnerData> getData() {
		return Optional.ofNullable(this.data);
	}

	/**
	 * API Version of {@link RadixNode}'s client
	 */
	public Optional<Integer> getVersion() {
		return Optional.ofNullable(this.version);
	}

	/**
	 * Universe configuration of {@link RadixNode}'s client
	 */
	public Optional<RadixUniverseConfig> getUniverseConfig() {
		return Optional.ofNullable(this.universeConfig);
	}
}
