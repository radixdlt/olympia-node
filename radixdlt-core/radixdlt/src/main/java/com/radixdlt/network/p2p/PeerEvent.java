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

package com.radixdlt.network.p2p;

import com.radixdlt.network.p2p.transport.PeerChannel;

import java.util.Objects;

public interface PeerEvent {

	final class PeerConnected implements PeerEvent {

		private final PeerChannel channel;

		public static PeerConnected create(PeerChannel channel) {
			return new PeerConnected(channel);
		}

		private PeerConnected(PeerChannel channel) {
			this.channel = channel;
		}

		public PeerChannel getChannel() {
			return this.channel;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			final var that = (PeerConnected) o;
			return Objects.equals(channel, that.channel);
		}

		@Override
		public int hashCode() {
			return Objects.hash(channel);
		}
	}

	final class PeerDisconnected implements PeerEvent {

		private final PeerChannel channel;

		public static PeerDisconnected create(PeerChannel channel) {
			return new PeerDisconnected(channel);
		}

		private PeerDisconnected(PeerChannel channel) {
			this.channel = channel;
		}

		public PeerChannel getChannel() {
			return this.channel;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			final var that = (PeerDisconnected) o;
			return Objects.equals(channel, that.channel);
		}

		@Override
		public int hashCode() {
			return Objects.hash(channel);
		}
	}

	final class PeerLostLiveness implements PeerEvent {

		private final NodeId nodeId;

		public static PeerLostLiveness create(NodeId nodeId) {
			return new PeerLostLiveness(nodeId);
		}

		private PeerLostLiveness(NodeId nodeId) {
			this.nodeId = nodeId;
		}

		public NodeId getNodeId() {
			return this.nodeId;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			final var that = (PeerLostLiveness) o;
			return Objects.equals(nodeId, that.nodeId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(nodeId);
		}
	}

}
