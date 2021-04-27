/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.network.p2p;

import com.radixdlt.consensus.bft.BFTNode;

import java.net.SocketAddress;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Retrieve the node's current peers
 */
public interface PeersView {

	final class PeerInfo {
		private NodeId nodeId;
		private Optional<RadixNodeUri> uri;
		private SocketAddress socketAddress;

		public static PeerInfo fromBftNode(BFTNode bftNode) {
			return new PeerInfo(NodeId.fromPublicKey(bftNode.getKey()), Optional.empty(), null);
		}

		public static PeerInfo create(NodeId nodeId, Optional<RadixNodeUri> uri, SocketAddress socketAddress) {
			return new PeerInfo(nodeId, uri, socketAddress);
		}

		private PeerInfo(NodeId nodeId, Optional<RadixNodeUri> uri, SocketAddress socketAddress) {
			this.nodeId = nodeId;
			this.uri = uri;
			this.socketAddress = socketAddress;
		}

		public NodeId getNodeId() {
			return nodeId;
		}

		public Optional<RadixNodeUri> getUri() {
			return uri;
		}

		public SocketAddress getSocketAddress() {
			return socketAddress;
		}

		public BFTNode bftNode() {
			return BFTNode.create(nodeId.getPublicKey());
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			PeerInfo peerInfo = (PeerInfo) o;
			return Objects.equals(nodeId, peerInfo.nodeId)
				&& Objects.equals(uri, peerInfo.uri)
				&& Objects.equals(socketAddress, peerInfo.socketAddress);
		}

		@Override
		public int hashCode() {
			return Objects.hash(nodeId, uri, socketAddress);
		}

		@Override
		public String toString() {
			return String.format("%s{%s}", this.getClass().getSimpleName(), this.nodeId);
		}
	}

	Stream<PeerInfo> peers();

	default boolean hasPeer(BFTNode bftNode) {
		return peers()
			.anyMatch(peer -> peer.nodeId.getPublicKey().equals(bftNode.getKey()));
	}
}
