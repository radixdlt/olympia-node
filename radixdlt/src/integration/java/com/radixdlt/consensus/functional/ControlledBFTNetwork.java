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

package com.radixdlt.consensus.functional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.BFTEventSender;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.crypto.ECPublicKey;
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
	private final ImmutableMap<MailboxId, LinkedList<Message>> messageQueue;

	ControlledBFTNetwork(ImmutableList<ECPublicKey> nodes) {
		this.nodes = nodes;
		this.messageQueue = nodes.stream()
			.flatMap(n0 -> nodes.stream().map(n1 -> new MailboxId(n0, n1)))
			.collect(
				ImmutableMap.toImmutableMap(
					key -> key,
					key -> new LinkedList<>()
				)
			);
	}

	static final class MailboxId {
		private final ECPublicKey sender;
		private final ECPublicKey receiver;

		MailboxId(ECPublicKey sender, ECPublicKey receiver) {
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
			if (!(obj instanceof MailboxId)) {
				return false;
			}

			MailboxId other = (MailboxId) obj;
			return this.sender.equals(other.sender) && this.receiver.equals(other.receiver);
		}

		@Override
		public String toString() {
			return sender.euid().toString().substring(0, 6) + " -> " + receiver.euid().toString().substring(0, 6);
		}
	}

	static final class Message {
		private final MailboxId mailboxId;
		private final Object msg;

		Message(ECPublicKey sender, ECPublicKey receiver, Object msg) {
			this.mailboxId = new MailboxId(sender, receiver);
			this.msg = msg;
		}

		public MailboxId getMailboxId() {
			return mailboxId;
		}

		public Object getMsg() {
			return msg;
		}

		public String toString() {
			return mailboxId + " " + msg;
		}
	}

	private void putMesssage(Message message) {
		messageQueue.get(message.getMailboxId()).add(message);
	}

	public List<Message> peekNextMessages() {
		return messageQueue.values()
			.stream()
			.filter(l -> !l.isEmpty())
			.map(LinkedList::getFirst)
			.collect(Collectors.toList());
	}

	public Object popNextMessage(MailboxId mailboxId) {
		return messageQueue.get(mailboxId).pop().getMsg();
	}

	public BFTEventSender getSender(ECPublicKey sender) {
		return new BFTEventSender() {
			@Override
			public void broadcastProposal(Proposal proposal) {
				for (ECPublicKey receiver : nodes) {
					putMesssage(new Message(sender, receiver, proposal));
				}
			}

			@Override
			public void sendNewView(NewView newView, ECPublicKey newViewLeader) {
				putMesssage(new Message(sender, newViewLeader, newView));
			}

			@Override
			public void sendVote(Vote vote, ECPublicKey leader) {
				putMesssage(new Message(sender, leader, vote));
			}
		};
	}
}
