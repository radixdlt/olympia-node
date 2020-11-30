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

package com.radixdlt.integration.distributed.simulation.network;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork.MessageInTransit;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Drops one proposal per view
 */
public class FProposalsPerViewDropper implements Predicate<MessageInTransit> {
	private final ConcurrentHashMap<View, Set<BFTNode>> proposalToDrop = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<View, Integer> proposalCount = new ConcurrentHashMap<>();
	private final ImmutableList<BFTNode> validatorSet;
	private final Random random;
	private final int faultySize;

	public FProposalsPerViewDropper(ImmutableList<BFTNode> validatorSet, Random random) {
		this.validatorSet = validatorSet;
		this.random = random;
		this.faultySize = (validatorSet.size() - 1) / 3;
	}

	public FProposalsPerViewDropper(ImmutableList<BFTNode> validatorSet) {
		this.validatorSet = validatorSet;
		this.random = null;
		this.faultySize = (validatorSet.size() - 1) / 3;
	}

	@Override
	public boolean test(MessageInTransit msg) {
		if (msg.getContent() instanceof Proposal) {
			final Proposal proposal = (Proposal) msg.getContent();
			final View view = proposal.getVertex().getView();
			final Set<BFTNode> nodesToDrop = proposalToDrop.computeIfAbsent(view, v -> {
				List<BFTNode> nodes = new LinkedList<>(validatorSet);
				ImmutableSet.Builder<BFTNode> nextFaultySet = ImmutableSet.builder();
				if (random != null) {
					for (int i = 0; i < faultySize; i++) {
						BFTNode nextFaultyNode = nodes.remove(random.nextInt(nodes.size()));
						nextFaultySet.add(nextFaultyNode);
					}
				} else {
					nodes.stream().limit(faultySize).forEach(nextFaultySet::add);
				}

				return nextFaultySet.build();
			});
			if (proposalCount.merge(view, 1, Integer::sum).equals(validatorSet.size())) {
				proposalToDrop.remove(view);
				proposalCount.remove(view);
			}

			return nodesToDrop.contains(msg.getReceiver());
		}

		return false;
	}
}
