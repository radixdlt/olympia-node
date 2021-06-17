/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.network.p2p.liveness;

import com.radixdlt.network.p2p.NodeId;

import java.util.Objects;

public final class PeerPingTimeout {

	private final NodeId nodeId;

	public static PeerPingTimeout create(NodeId nodeId) {
		return new PeerPingTimeout(nodeId);
	}

	private PeerPingTimeout(NodeId nodeId) {
		this.nodeId = nodeId;
	}

	public NodeId getNodeId() {
		return nodeId;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final var that = (PeerPingTimeout) o;
		return Objects.equals(nodeId, that.nodeId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(nodeId);
	}
}
