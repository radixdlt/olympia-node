/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.consensus.deterministic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.BFTEventSender;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.VertexStore.SyncSender;
import com.radixdlt.consensus.Vote;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A BFT network supporting the EventCoordinatorNetworkSender interface which
 * stores each message in a queue until they are synchronously popped.
 *
 * This class is not thread safe.
 */
public final class ControlledBFTNetwork {
	private final ImmutableList<ECPublicKey> nodes;
	private final ImmutableMap<ChannelId, LinkedList<ControlledMessage>> messageQueue;

	ControlledBFTNetwork(ImmutableList<ECPublicKey> nodes) {
		this.nodes = nodes;
		this.messageQueue = nodes.stream()
			.flatMap(n0 -> nodes.stream().map(n1 -> new ChannelId(n0, n1)))
			.collect(
				ImmutableMap.toImmutableMap(
					key -> key,
					key -> new LinkedList<>()
				)
			);
	}

	static final class ChannelId {
		private final ECPublicKey sender;
		private final ECPublicKey receiver;

		ChannelId(ECPublicKey sender, ECPublicKey receiver) {
			this.sender = sender;
			this.receiver = receiver;
		}

		public ECPublicKey getReceiver() {
			return receiver;
		}

		public ECPublicKey getSender() {
			return sender;
		}

		@Override
		public int hashCode() {
			return Objects.hash(sender, receiver);
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof ChannelId)) {
				return false;
			}

			ChannelId other = (ChannelId) obj;
			return this.sender.equals(other.sender) && this.receiver.equals(other.receiver);
		}

		@Override
		public String toString() {
			return sender.euid().toString().substring(0, 6) + " -> " + receiver.euid().toString().substring(0, 6);
		}
	}

	static final class ControlledMessage {
		private final ChannelId channelId;
		private final Object msg;

		ControlledMessage(ECPublicKey sender, ECPublicKey receiver, Object msg) {
			this.channelId = new ChannelId(sender, receiver);
			this.msg = msg;
		}

		public ChannelId getChannelId() {
			return channelId;
		}

		public Object getMsg() {
			return msg;
		}

		public String toString() {
			return channelId + " " + msg;
		}
	}

	private void putMesssage(ControlledMessage controlledMessage) {
		messageQueue.get(controlledMessage.getChannelId()).add(controlledMessage);
	}

	public List<ControlledMessage> peekNextMessages() {
		return messageQueue.values()
			.stream()
			.filter(l -> !l.isEmpty())
			.map(LinkedList::getFirst)
			.collect(Collectors.toList());
	}

	public Object popNextMessage(ChannelId channelId) {
		return messageQueue.get(channelId).pop().getMsg();
	}

	public ControlledSender getSender(ECPublicKey sender) {
		return new ControlledSender() {
			@Override
			public void synced(Hash vertexId) {
				putMesssage(new ControlledMessage(sender, sender, vertexId));
			}

			@Override
			public void broadcastProposal(Proposal proposal) {
				for (ECPublicKey receiver : nodes) {
					putMesssage(new ControlledMessage(sender, receiver, proposal));
				}
			}

			@Override
			public void sendNewView(NewView newView, ECPublicKey newViewLeader) {
				putMesssage(new ControlledMessage(sender, newViewLeader, newView));
			}

			@Override
			public void sendVote(Vote vote, ECPublicKey leader) {
				putMesssage(new ControlledMessage(sender, leader, vote));
			}
		};
	}

	interface ControlledSender extends BFTEventSender, SyncSender {
	}
}
