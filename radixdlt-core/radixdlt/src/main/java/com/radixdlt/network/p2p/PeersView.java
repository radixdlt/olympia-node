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

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.bft.BFTNode;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Retrieve the node's current peers
 */
public interface PeersView {

	final class PeerChannelInfo {
		private Optional<RadixNodeUri> uri;
		private InetSocketAddress socketAddress;
		private boolean isOutbound;

		public static PeerChannelInfo create(Optional<RadixNodeUri> uri, InetSocketAddress socketAddress, boolean isOutbound) {
			return new PeerChannelInfo(uri, socketAddress, isOutbound);
		}

		private PeerChannelInfo(Optional<RadixNodeUri> uri, InetSocketAddress socketAddress, boolean isOutbound) {
			this.uri = uri;
			this.socketAddress = socketAddress;
			this.isOutbound = isOutbound;
		}

		public Optional<RadixNodeUri> getUri() {
			return uri;
		}

		public InetSocketAddress getSocketAddress() {
			return socketAddress;
		}

		public boolean isOutbound() {
			return isOutbound;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			final var other = (PeerChannelInfo) o;
			return Objects.equals(uri, other.uri)
				&& Objects.equals(socketAddress, other.socketAddress)
				&& Objects.equals(isOutbound, other.isOutbound);
		}

		@Override
		public int hashCode() {
			return Objects.hash(uri, socketAddress, isOutbound);
		}
	}

	final class PeerInfo {
		private NodeId nodeId;
		private ImmutableList<PeerChannelInfo> channels;

		public static PeerInfo fromBftNode(BFTNode bftNode) {
			return new PeerInfo(NodeId.fromPublicKey(bftNode.getKey()), ImmutableList.of());
		}

		public static PeerInfo create(NodeId nodeId, ImmutableList<PeerChannelInfo> channels) {
			return new PeerInfo(nodeId, channels);
		}

		private PeerInfo(NodeId nodeId, ImmutableList<PeerChannelInfo> channels) {
			this.nodeId = nodeId;
			this.channels = channels;
		}

		public NodeId getNodeId() {
			return nodeId;
		}

		public ImmutableList<PeerChannelInfo> getChannels() {
			return channels;
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
			final var other = (PeerInfo) o;
			return Objects.equals(nodeId, other.nodeId)
				&& Objects.equals(channels, other.channels);
		}

		@Override
		public int hashCode() {
			return Objects.hash(nodeId, channels);
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
