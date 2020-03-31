/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.consensus;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.examples.tictactoe.Pair;
import io.reactivex.rxjava3.core.Observable;
import org.assertj.core.api.Condition;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * Tests with networks with crashed nodes
 */
public class CrashFaultNetworkTest {
	static List<ECKeyPair> createNodes(int numNodes, String designation) {
		return Stream.generate(() -> {
			// efficient? no. useful? yes.
			ECKeyPair keyPair;
			do {
				keyPair = ECKeyPair.generateNew();
			} while (!keyPair.euid().toString().startsWith(designation));
			return keyPair;
		}).limit(numNodes).collect(Collectors.toList());
	}

	private void crashNode(ECKeyPair node, BFTTestNetwork bftNetwork) {
		bftNetwork.getTestEventCoordinatorNetwork().setReceivingDisable(node.euid(), true);
		bftNetwork.getTestEventCoordinatorNetwork().setSendingDisable(node.euid(), true);
	}

	@Test
	public void given_2_out_of_3_correct_bft_instances__then_all_instances_should_only_get_genesis_commit_over_1_minute() {
		final int numNodes = 3;
		final int numCrashed = 1;
		final int numCorrect = numNodes - numCrashed;
		final long time = 1;
		final TimeUnit timeUnit = TimeUnit.MINUTES;

		final List<ECKeyPair> correctNodes = createNodes(numCorrect, "c");
		final List<ECKeyPair> faultyNodes = createNodes(numCrashed, "f");
		final List<ECKeyPair> allNodes = Stream.concat(correctNodes.stream(), faultyNodes.stream()).collect(Collectors.toList());
		final BFTTestNetwork bftNetwork = new BFTTestNetwork(allNodes);
		crashNode(allNodes.get(2), bftNetwork);

		List<Observable<Vertex>> committedObservables = allNodes.stream()
			.map(bftNetwork::getVertexStore)
			.map(VertexStore::lastCommittedVertex)
			.collect(Collectors.toList());

		Observable<Vertex> committed = Observable.zip(committedObservables, Arrays::stream)
			.map(committedVertices -> committedVertices.distinct().collect(Collectors.toList()))
			.take(time, timeUnit)
			.singleOrError()
			.doAfterSuccess(committedVertices -> assertThat(committedVertices).hasSize(1))
			.map(vertices -> (Vertex) vertices.get(0))
			.doAfterSuccess(v -> System.out.println("Committed " + v))
			.toObservable();

		bftNetwork.processBFT().takeUntil(committed)
			.blockingSubscribe();
	}

	@Test
	public void given_3_out_of_4_correct_bfts__then_correct_instances_should_get_same_commits_consecutive_vertices_over_1_minute() {
		final int numNodes = 4;
		final int numCrashed = 1;
		final int numCorrect = numNodes - numCrashed;
		final long time = 1;
		final TimeUnit timeUnit = TimeUnit.MINUTES;

		final List<ECKeyPair> correctNodes = createNodes(numCorrect, "c");
		final List<ECKeyPair> faultyNodes = createNodes(numCrashed, "f");
		final List<ECKeyPair> allNodes = Stream.concat(correctNodes.stream(), faultyNodes.stream()).collect(Collectors.toList());
		final Set<ECPublicKey> correctNodesPubs = correctNodes.stream()
			.map(ECKeyPair::getPublicKey)
			.collect(Collectors.toSet());
		final BFTTestNetwork bftNetwork = new BFTTestNetwork(allNodes);
		// "crash" all faulty nodes by disallowing any communication
		faultyNodes.forEach(node -> crashNode(node, bftNetwork));

		// correct nodes should all get the same commits in the same order
		Observable<Object> correctCommitCheck = Observable.zip(
				correctNodes.stream()
					.map(bftNetwork::getVertexStore)
					.map(VertexStore::lastCommittedVertex)
					.collect(Collectors.toList()),
				Arrays::stream)
			.map(committedVertices -> committedVertices.distinct().collect(Collectors.toList()))
			.doOnNext(committedVertices -> assertThat(committedVertices).hasSize(1))
			.map(vertices -> (Vertex) vertices.get(0))
			.map(o -> o);

		// correct nodes should only get timeouts when crashed nodes were a proposer
		Observable<Object> correctTimeoutCheck = Observable.interval(1, TimeUnit.SECONDS)
			.flatMapIterable(i -> correctNodes)
			.doOnNext(cn -> assertThat(bftNetwork.getCounters(cn).getCount(Counters.CounterType.TIMEOUT))
				.satisfies(new Condition<>(c -> c == (bftNetwork.getPacemaker(cn).getCurrentView().number() / numNodes) * numCrashed,
					"Timeout counter is equal to number of times crashed nodes were proposer.")))
			.map(o -> o);

		// correct proposals should be direct if generated after another correct proposal, otherwise there should be a gap
		List<Observable<Vertex>> correctProposals = correctNodes.stream()
			.map(ECKeyPair::euid)
			.map(bftNetwork.getTestEventCoordinatorNetwork()::getNetworkRx)
			.map(EventCoordinatorNetworkRx::proposalMessages)
			.collect(Collectors.toList());
		Observable<Object> directProposalsCheck = Observable.merge(correctProposals)
			.filter(v -> correctNodesPubs.contains(bftNetwork.getProposerElection().getProposer(v.getView().previous())))
			.doOnNext(v -> assertThat(v)
				.satisfies(new Condition<>(vtx -> vtx.getView().equals(vtx.getParentView().next()),
					"Vertex after correct %s at %s has direct parent", bftNetwork.getProposerElection().getProposer(v.getParentView()).euid(), v.getParentView())))
			.map(o -> o);
		Observable<Object> gapProposalsCheck = Observable.merge(correctProposals)
			.filter(v -> !correctNodesPubs.contains(bftNetwork.getProposerElection().getProposer(v.getView().previous())))
			.doOnNext(v -> assertThat(v)
				.satisfies(new Condition<>(vtx -> !vtx.getView().equals(vtx.getParentView().next()),
					"Vertex after faulty %s at %s has gap", bftNetwork.getProposerElection().getProposer(v.getParentView()).euid(), v.getParentView())))
			.map(o -> o);

		Observable.mergeArray(bftNetwork.processBFT(), correctCommitCheck, correctTimeoutCheck, directProposalsCheck, gapProposalsCheck)
			.take(time, timeUnit)
			.blockingSubscribe();
	}

	@Test
	public void given_6_initially_correct_bfts_that_randomly_crash_stop__then_correct_instances_should_get_same_commits_consecutive_vertices_until_quorums_are_impossible_over_1_minute() {
		final int numNodes = 6;
		final long time = 1;
		final TimeUnit timeUnit = TimeUnit.MINUTES;
		final long rngSeed = System.currentTimeMillis();
		final double crashProbabilityPerSecond = 0.1; // probability that a single node out of all correct nodes will crash-stop

		final List<ECKeyPair> allNodes = createNodes(numNodes, "");
		final Set<ECPublicKey> faultyNodesPubs = new HashSet<>();
		final BFTTestNetwork bftNetwork = new BFTTestNetwork(allNodes);

		// correct nodes should all get the same commits in the same order
		Observable<Object> correctCommitCheck = Observable.zip(
				allNodes.stream()
					.map(node -> bftNetwork.getVertexStore(node).lastCommittedVertex()
						.map(vertex -> Pair.of(node, vertex)))
					.collect(Collectors.toList()),
				Arrays::stream)
			.map(nodesAndVertices -> nodesAndVertices
				.map(nodeAndVertex -> (Pair<ECPublicKey, Vertex>) nodeAndVertex)
				.filter(nodeAndVertex -> !faultyNodesPubs.contains(nodeAndVertex.getFirst()))
				.map(Pair::getSecond)
				.distinct()
				.collect(Collectors.toList()))
			.doOnNext(committedVertices -> assertThat(committedVertices).hasSize(1))
			.map(vertices -> vertices.get(0))
			.map(o -> o);

		// randomly seduce nodes to be naughty and pretend to crash-stop
		Random rng = new Random(rngSeed);
		Observable<Object> seducer = Observable.interval(1, TimeUnit.SECONDS)
			.filter(i -> faultyNodesPubs.size() < allNodes.size() && rng.nextDouble() < crashProbabilityPerSecond)
			.map(i -> rng.nextInt(allNodes.size() - faultyNodesPubs.size()))
			.map(allNodes::get)
			.doOnNext(node -> faultyNodesPubs.add(node.getPublicKey()))
			.doOnNext(node -> crashNode(node, bftNetwork))
			.doAfterNext(node -> System.out.println("Crashed " + node.euid()))
			.map(o -> o);

		Observable.mergeArray(bftNetwork.processBFT(), correctCommitCheck, seducer)
			.take(time, timeUnit)
			.blockingSubscribe();
	}

}
