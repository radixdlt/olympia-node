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

package com.radixdlt.consensus.simulation.asynchronous;

import com.radixdlt.consensus.EventCoordinatorNetworkRx;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexStore;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.simulation.BFTNetworkSimulation;
import com.radixdlt.consensus.simulation.DroppingLatencyProvider;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.examples.tictactoe.Pair;

import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork;
import io.reactivex.rxjava3.core.Observable;
import org.assertj.core.api.Condition;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * Tests with networks with crash-stopped nodes (that do not recover).
 * These tests comprise both static and dynamic configurations (where nodes crash before or during runtime, respectively).
 */
public class CrashFaultNetworkTest {
	static List<ECKeyPair> createNodes(int numNodes) {
		return Stream.generate(ECKeyPair::generateNew).limit(numNodes).collect(Collectors.toList());
	}

	/**
	 * Tests a static configuration of 3 nodes (1 crash-stopped), meaning a QC can never be formed.
	 * The intended behaviour is that all instances retain the genesis commit as their latest committed vertex
	 * since no progress can be made.
	 */
	@Test
	public void given_2_out_of_3_correct_bft_instances__then_all_instances_should_only_get_genesis_commit_over_1_minute() {
		final int numNodes = 3;
		final int numCrashed = 1;
		final int numCorrect = numNodes - numCrashed;
		final long time = 1;
		final TimeUnit timeUnit = TimeUnit.MINUTES;

		final List<ECKeyPair> correctNodes = createNodes(numCorrect);
		final List<ECKeyPair> faultyNodes = createNodes(numCrashed);
		final List<ECKeyPair> allNodes = Stream.concat(correctNodes.stream(), faultyNodes.stream()).collect(Collectors.toList());
		final DroppingLatencyProvider crashLatencyProvider = new DroppingLatencyProvider();
		final TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder()
			.latencyProvider(crashLatencyProvider)
			.build();

		final BFTNetworkSimulation bftNetwork = new BFTNetworkSimulation(allNodes, network);
		crashLatencyProvider.crashNode(allNodes.get(2).getPublicKey());

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

	/**
	 * Tests a static configuration of 4 nodes (1 crash-stopped), meaning a QC *can* be formed.
	 * The intended behaviour is that the correct instances make progress,
	 * "skipping" the faulty node as a leader when required.
	 */
	@Test
	public void given_3_out_of_4_correct_bfts__then_correct_instances_should_get_same_commits_consecutive_vertices_over_1_minute() {
		final int numNodes = 4;
		final int numCrashed = 1;
		final int numCorrect = numNodes - numCrashed;
		final long time = 1;
		final TimeUnit timeUnit = TimeUnit.MINUTES;

		final List<ECKeyPair> correctNodes = createNodes(numCorrect);
		final List<ECKeyPair> faultyNodes = createNodes(numCrashed);
		final List<ECKeyPair> allNodes = Stream.concat(correctNodes.stream(), faultyNodes.stream()).collect(Collectors.toList());
		final Set<ECPublicKey> correctNodesPubs = correctNodes.stream()
			.map(ECKeyPair::getPublicKey)
			.collect(Collectors.toSet());
		final DroppingLatencyProvider crashLatencyProvider = new DroppingLatencyProvider();
		final TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder()
			.latencyProvider(crashLatencyProvider)
			.build();
		final BFTNetworkSimulation bftNetwork = new BFTNetworkSimulation(allNodes, network);
		// "crash" all faulty nodes by disallowing any communication
		faultyNodes.forEach(node -> crashLatencyProvider.crashNode(node.getPublicKey()));


		// there should be a new highest QC every once in a while to ensure progress
		// the minimum latency per round is determined using the network latency and a tolerance
		final int maxLatency = TestEventCoordinatorNetwork.DEFAULT_LATENCY;
		int worstCaseLatencyPerRound = maxLatency * 2 // base latency: two rounds in the normal case
			+ numCrashed * (maxLatency * 4 + bftNetwork.getPacemakerTimeout()); // four rounds plus timeout in bad case
		// account for any inaccuracies, execution time, scheduling inefficiencies..
		// the tolerance is high since we're only interested in qualitative progress in this test
		double tolerance = 2.0;
		int minimumLatencyPerRound = (int) (worstCaseLatencyPerRound * tolerance);
		AtomicReference<View> highestQCView = new AtomicReference<>(View.genesis());
		Observable<Object> progressCheck = Observable.interval(minimumLatencyPerRound, minimumLatencyPerRound, TimeUnit.MILLISECONDS)
			.map(i -> allNodes.stream()
				.map(bftNetwork::getVertexStore)
				.map(VertexStore::getHighestQC)
				.map(QuorumCertificate::getView)
				.max(View::compareTo)
				.get()) // there must be some max highest QC unless allNodes is empty
			.doOnNext(view -> assertThat(view)
				.satisfies(new Condition<>(v -> v.compareTo(highestQCView.get()) > 0,
					"The highest highestQC %s increased since last highestQC %s after %d ms", view, highestQCView.get(), minimumLatencyPerRound)))
			.doOnNext(highestQCView::set)
			.doOnNext(newHighestQCView -> System.out.println("Made progress to new highest QC view " + highestQCView))
			.map(o -> o);

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
			// there is a race condition between getCount(TIMEOUT) and getCurrentView in pacemaker
			// however, since we're only interested in having at most X timeouts, we can safely just check for <=
			.doOnNext(cn -> assertThat(bftNetwork.getCounters(cn).get(SystemCounters.CounterType.CONSENSUS_TIMEOUT))
				.satisfies(new Condition<>(c -> c <= (bftNetwork.getPacemaker(cn).getCurrentView().number() / numNodes) * numCrashed,
					"Timeout counter is less or equal to number of times crashed nodes were proposer.")))
			.map(o -> o);

		// correct proposals should be direct if generated after another correct proposal, otherwise there should be a gap
		List<Observable<Vertex>> correctProposals = correctNodes.stream()
			.map(ECKeyPair::getPublicKey)
			.map(bftNetwork.getUnderlyingNetwork()::getNetworkRx)
			.map(EventCoordinatorNetworkRx::consensusEvents)
			.map(p -> p.ofType(Proposal.class).map(Proposal::getVertex))
			.collect(Collectors.toList());
		Observable<Object> directProposalsCheck = Observable.merge(correctProposals)
			.filter(v -> correctNodesPubs.contains(bftNetwork.getProposerElection().getProposer(v.getView().previous())))
			.doOnNext(v -> assertThat(v)
				.satisfies(new Condition<>(vtx -> vtx.getView().equals(vtx.getParentView().next()),
					"Vertex after correct %s at %s has direct parent",
					bftNetwork.getProposerElection().getProposer(v.getParentView()).euid(), v.getParentView())))
			.map(o -> o);
		Observable<Object> gapProposalsCheck = Observable.merge(correctProposals)
			.filter(v -> !correctNodesPubs.contains(bftNetwork.getProposerElection().getProposer(v.getView().previous())))
			.doOnNext(v -> assertThat(v)
				.satisfies(new Condition<>(vtx -> !vtx.getView().equals(vtx.getParentView().next()),
					"Vertex after faulty %s at %s has gap",
					bftNetwork.getProposerElection().getProposer(v.getParentView()).euid(), v.getParentView())))
			.map(o -> o);

		List<Observable<Object>> checks = Arrays.asList(
			correctCommitCheck, progressCheck, correctTimeoutCheck, directProposalsCheck, gapProposalsCheck
		);
		Observable.mergeArray(bftNetwork.processBFT(), Observable.merge(checks))
			.take(time, timeUnit)
			.blockingSubscribe();
	}

	/**
	 * Tests a dynamic configuration of 7 nodes (all correct at start),
	 * where correct nodes are crash-stopped at random over time.
	 * The intended behaviour is that the correct nodes keep making progress until QCs can no longer be formed.
	 */
	@Test
	public void given_7_correct_bfts_that_randomly_crash__then_correct_instances_should_make_progress_as_possible_over_1_minute() {
		final int numNodes = 7;
		final int maxToleratedFaultyNodes = (numNodes - 1) / 3;
		final long time = 1;
		final TimeUnit timeUnit = TimeUnit.MINUTES;
		final long rngSeed = System.currentTimeMillis();
		// probability that a single node out of all correct nodes will crash-stop
		final double crashProbabilityPerSecond = 0.1;

		final List<ECKeyPair> allNodes = createNodes(numNodes);
		final Set<ECPublicKey> faultyNodesPubs = new HashSet<>();
		final DroppingLatencyProvider crashLatencyProvider = new DroppingLatencyProvider();
		final TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder()
			.latencyProvider(crashLatencyProvider)
			.build();
		final BFTNetworkSimulation bftNetwork = new BFTNetworkSimulation(allNodes, network);

		// correct nodes should all get the same commits in the same order
		Observable<Object> correctCommitCheck = Observable.zip(
			allNodes.stream()
				.map(node -> bftNetwork.getVertexStore(node).lastCommittedVertex()
					.map(vertex -> Pair.of(node, vertex)))
				.collect(Collectors.toList()),
				this::toStream)
			.map(nodesAndVertices -> nodesAndVertices
				.filter(nodeAndVertex -> !faultyNodesPubs.contains(nodeAndVertex.getFirst().getPublicKey()))
				.map(Pair::getSecond)
				.distinct()
				.collect(Collectors.toList()))
			.doOnNext(committedVertices -> assertThat(committedVertices).hasSize(1))
			.map(vertices -> vertices.get(0))
			.map(o -> o);

		// there should be a new highest QC every once in a while to ensure progress
		// the minimum latency per round is determined using the network latency and a tolerance
		final int maxLatency = TestEventCoordinatorNetwork.DEFAULT_LATENCY;
		int worstCaseLatencyPerRound = maxLatency * 2 // base latency: two rounds in the normal case
			+ numNodes * (maxLatency * 4 + bftNetwork.getPacemakerTimeout()); // four rounds plus timeout in bad case
		// account for any inaccuracies, execution time, scheduling inefficiencies..
		// the tolerance is high since we're only interested in qualitative progress in this test
		double tolerance = 2.0;
		int minimumLatencyPerRound = (int) (worstCaseLatencyPerRound * tolerance);
		AtomicReference<View> highestQCView = new AtomicReference<>(View.genesis());
		Observable<Object> progressCheck = Observable.interval(minimumLatencyPerRound, minimumLatencyPerRound, TimeUnit.MILLISECONDS)
			.filter(i -> faultyNodesPubs.size() <= maxToleratedFaultyNodes)
			.map(i -> allNodes.stream()
				.map(bftNetwork::getVertexStore)
				.map(VertexStore::getHighestQC)
				.map(QuorumCertificate::getView)
				.max(View::compareTo)
				.get()) // there must be some max highest QC unless allNodes is empty
			.doOnNext(view -> assertThat(view)
				.satisfies(new Condition<>(v -> v.compareTo(highestQCView.get()) > 0,
					"The highest highestQC %s increased since last highestQC %s after %d ms", view, highestQCView.get(), minimumLatencyPerRound)))
			.doOnNext(highestQCView::set)
			.doOnNext(newHighestQCView -> System.out.println("Made progress to new highest QC view " + highestQCView))
			.map(o -> o);

		// randomly seduce nodes to be naughty and pretend to crash-stop
		Random rng = new Random(rngSeed);
		Observable<Object> seducer = Observable.interval(1, TimeUnit.SECONDS)
			.filter(i -> faultyNodesPubs.size() < allNodes.size() && rng.nextDouble() < crashProbabilityPerSecond)
			.map(i -> rng.nextInt(allNodes.size() - faultyNodesPubs.size()))
			.map(allNodes::get)
			.doOnNext(node -> faultyNodesPubs.add(node.getPublicKey()))
			.doOnNext(node -> crashLatencyProvider.crashNode(node.getPublicKey()))
			.doAfterNext(node -> System.out.println("Crashed " + node.euid()))
			.map(o -> o);

		List<Observable<Object>> checks = Arrays.asList(correctCommitCheck, progressCheck, seducer);
		Observable.mergeArray(bftNetwork.processBFT(), Observable.merge(checks))
			.take(time, timeUnit)
			.blockingSubscribe();
	}

	private Stream<Pair<ECKeyPair, Vertex>> toStream(Object[] input) {
		// Unfortunately Observable.zip has an interface that can't possibly be statically typesafe
		return Arrays.stream(input)
			.map(Pair.class::cast)
			.map(p -> Pair.of(ECKeyPair.class.cast(p.getFirst()), Vertex.class.cast(p.getSecond())));
	}
}
