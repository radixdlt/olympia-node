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

package com.radixdlt.consensus.simulation.configuration;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.View;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.consensus.simulation.network.SimulationNetwork.MessageInTransit;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Drops one proposal per view
 */
public class OneProposalPerViewDropper implements Predicate<MessageInTransit> {
	private final Map<View, ECPublicKey> proposalToDrop = new HashMap<>();
	private final Map<View, Integer> proposalCount = new HashMap<>();
	private final ImmutableList<ECPublicKey> nodes;
	private final Random random;

	public OneProposalPerViewDropper(ImmutableList<ECPublicKey> nodes, Random random) {
		this.nodes = nodes;
		this.random = random;
	}

	@Override
	public boolean test(MessageInTransit msg) {
		if (msg.getContent() instanceof Proposal) {
			final Proposal proposal = (Proposal) msg.getContent();
			final View view = proposal.getVertex().getView();
			final ECPublicKey nodeToDrop = proposalToDrop.computeIfAbsent(view, v -> nodes.get(random.nextInt(nodes.size())));
			if (proposalCount.merge(view, 1, Integer::sum).equals(nodes.size())) {
				proposalToDrop.remove(view);
				proposalCount.remove(view);
			}

			return msg.getReceiver().equals(nodeToDrop);
		}

		return false;
	}
}
