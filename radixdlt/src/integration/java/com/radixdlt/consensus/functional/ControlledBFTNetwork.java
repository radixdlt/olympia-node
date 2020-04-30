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
import java.util.function.Function;

/**
 * A BFT network supporting the EventCoordinatorNetworkSender interface which
 * stores each message in a queue until they are synchronously popped.
 *
 * This class is not thread safe.
 */
public final class ControlledBFTNetwork {
	private final ImmutableList<ECPublicKey> nodes;
	private final ImmutableMap<ECPublicKey, ImmutableMap<ECPublicKey, LinkedList<Object>>> messages;

	public ControlledBFTNetwork(ImmutableList<ECPublicKey> nodes) {
		this.nodes = nodes;
		this.messages = nodes.stream()
			.collect(
				ImmutableMap.toImmutableMap(
					sender -> sender,
					sender -> nodes.stream().collect(ImmutableMap.toImmutableMap(receiver -> receiver, receiver -> new LinkedList<>()))
				)
			);
	}

	private void putMesssage(ECPublicKey sender, ECPublicKey receiver, Object message) {
		this.messages.get(sender).get(receiver).add(message);
	}

	public Function<ECPublicKey, Object> getReceiver(ECPublicKey receiver) {
		return sender -> this.messages.get(sender).get(receiver).pop();
	}

	public BFTEventSender getSender(ECPublicKey sender) {
		return new BFTEventSender() {
			@Override
			public void broadcastProposal(Proposal proposal) {
				for (ECPublicKey receiver : nodes) {
					putMesssage(sender, receiver, proposal);
				}
			}

			@Override
			public void sendNewView(NewView newView, ECPublicKey newViewLeader) {
				putMesssage(sender, newViewLeader, newView);
			}

			@Override
			public void sendVote(Vote vote, ECPublicKey leader) {
				putMesssage(sender, leader, vote);
			}
		};
	}
}
