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

package com.radixdlt.consensus.deterministic.tests.bft.synchronous;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.deterministic.DeterministicTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

public class FProposalDropperResponsiveTest {
	private static final int NUM_STEPS = 30000;

	private final Random random = new Random(123456789);

	private void runFProposalDropperResponsiveTest(int numNodes, Function<View, Set<Integer>> nodesToDropFunction) {
		final Map<View, Set<Integer>> proposalsToDrop = new HashMap<>();
		final Map<View, Integer> proposalCount = new HashMap<>();

		final DeterministicTest test = DeterministicTest.createSingleEpochAlwaysSyncedTest(numNodes);
		test.start();
		for (int step = 0; step < NUM_STEPS; step++) {
			test.processNextMsg(random, (receiverId, msg) -> {
				if (msg instanceof Proposal) {
					final Proposal proposal = (Proposal) msg;
					final View view = proposal.getVertex().getView();
					final Set<Integer> nodesToDrop = proposalsToDrop.computeIfAbsent(view, nodesToDropFunction);

					if (proposalCount.merge(view, 1, Integer::sum).equals(numNodes)) {
						proposalsToDrop.remove(view);
						proposalCount.remove(view);
					}

					return !nodesToDrop.contains(receiverId);
				}

				return true;
			});
		}
	}

	private void runRandomMaliciousNodesTest(int numNodes) {
		this.runFProposalDropperResponsiveTest(
			numNodes,
			v -> {
				List<Integer> nodes = Stream.iterate(0, a -> a + 1).limit(numNodes).collect(Collectors.toList());
				return Stream.iterate(0, a -> a + 1)
					.limit((numNodes - 1) / 3)
					.map(i -> {
						int index = random.nextInt(nodes.size());
						return nodes.remove(index);
					})
					.collect(Collectors.toSet());
			}
		);
	}


	@Test
	public void when_run_4_correct_nodes_with_random_proposal_dropper_and_timeouts_disabled__then_bft_should_be_responsive() {
		this.runRandomMaliciousNodesTest(4);
	}

	@Test
	public void when_run_5_correct_nodes_with_random_proposal_dropper_and_timeouts_disabled__then_bft_should_be_responsive() {
		this.runRandomMaliciousNodesTest(5);
	}

	@Test
	public void when_run_10_correct_nodes_with_random_proposal_dropper_and_timeouts_disabled__then_bft_should_be_responsive() {
		this.runRandomMaliciousNodesTest(10);
	}

	@Test
	public void when_run_50_correct_nodes_with_random_proposal_dropper_and_timeouts_disabled__then_bft_should_be_responsive() {
		this.runRandomMaliciousNodesTest(50);
	}

	@Test
	public void when_run_100_correct_nodes_with_random_proposal_dropper_and_timeouts_disabled__then_bft_should_be_responsive() {
		this.runRandomMaliciousNodesTest(100);
	}

	private void runStaticMaliciousNodesTest(int numNodes) {
		Set<Integer> nodes = Stream.iterate(0, a -> a + 1).limit((numNodes - 1) / 3).collect(ImmutableSet.toImmutableSet());
		this.runFProposalDropperResponsiveTest(
			numNodes,
			v -> nodes
		);
	}

	@Test
	public void when_run_4_correct_nodes_with_single_node_proposal_dropper_and_timeouts_disabled__then_bft_should_be_responsive() {
		this.runStaticMaliciousNodesTest(4);
	}

	@Test
	public void when_run_5_correct_nodes_with_single_node_proposal_dropper_and_timeouts_disabled__then_bft_should_be_responsive() {
		this.runStaticMaliciousNodesTest(5);
	}

	@Test
	public void when_run_10_correct_nodes_with_single_node_proposal_dropper_and_timeouts_disabled__then_bft_should_be_responsive() {
		this.runStaticMaliciousNodesTest(10);
	}

	@Test
	public void when_run_50_correct_nodes_with_single_node_proposal_dropper_and_timeouts_disabled__then_bft_should_be_responsive() {
		this.runStaticMaliciousNodesTest(50);
	}

	@Test
	public void when_run_100_correct_nodes_with_single_node_proposal_dropper_and_timeouts_disabled__then_bft_should_be_responsive() {
		this.runStaticMaliciousNodesTest(100);
	}
}
