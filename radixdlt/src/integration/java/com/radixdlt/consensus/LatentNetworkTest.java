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

import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.network.TestEventCoordinatorNetwork;
import io.reactivex.rxjava3.core.Observable;
import org.assertj.core.api.Condition;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * Tests with networks with imperfect and randomly latent in-order communication channels.
 * These tests comprise only static configurations of exclusively correct nodes.
 */
public class LatentNetworkTest {
	private static final int MINIMUM_NETWORK_LATENCY = 10;
	// 2 times max latency should be less than BFTTestNetwork.TEST_PACEMAKER_TIMEOUT
	// so we don't get unwanted pacemaker timeouts
	private static final int MAXIMUM_NETWORK_LATENCY = 100;

	static List<ECKeyPair> createNodes(int numNodes) {
		return Stream.generate(ECKeyPair::generateNew).limit(numNodes).collect(Collectors.toList());
	}

	static BFTTestNetwork createNetwork(List<ECKeyPair> nodes) {
		return new BFTTestNetwork(
			nodes,
			TestEventCoordinatorNetwork.orderedRandomlyLatent(MINIMUM_NETWORK_LATENCY, MAXIMUM_NETWORK_LATENCY)
		);
	}

	/**
	 * Tests a static configuration of 4 correct nodes with randomly latent in-order communication.
	 * The intended behaviour is that all correct instances make progress and eventually align in their commits.
	 */
	@Test
	public void given_4_correct_bfts_in_latent_network__then_all_instances_should_get_same_commits_consecutive_vertices_eventually_over_1_minute() {
		final int numNodes = 4;
		final long time = 1;
		final TimeUnit timeUnit = TimeUnit.MINUTES;

		final List<ECKeyPair> allNodes = createNodes(numNodes);
		final BFTTestNetwork bftNetwork = createNetwork(allNodes);

		// there should be a new highest QC every once in a while to ensure progress
		// the minimum latency per round is determined using the network latency and a tolerance
		int worstCaseLatencyPerRound = bftNetwork.getMaximumNetworkLatency() * 2; // base latency: two rounds in the normal case
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
			.doOnNext(newHighestQCView -> System.out.println("Progressed to new highest QC view " + highestQCView))
			.map(o -> o);

		// correct nodes should all get the same commits in the same order
		Observable<Object> correctCommitCheck = Observable.zip(
			allNodes.stream()
				.map(bftNetwork::getVertexStore)
				.map(VertexStore::lastCommittedVertex)
				.collect(Collectors.toList()),
			Arrays::stream)
			.map(committedVertices -> committedVertices.distinct().collect(Collectors.toList()))
			.doOnNext(committedVertices -> assertThat(committedVertices).hasSize(1))
			.map(vertices -> (Vertex) vertices.get(0))
			.map(o -> o);

		// correct nodes should get no timeouts since max latency is smaller than timeout
		Observable<Object> correctTimeoutCheck = Observable.interval(1, TimeUnit.SECONDS)
			.flatMapIterable(i -> allNodes)
			.doOnNext(cn -> assertThat(bftNetwork.getCounters(cn).get(CounterType.CONSENSUS_TIMEOUT))
				.satisfies(new Condition<>(c -> c == 0,
					"Timeout counter is zero in correct node %s", cn.getPublicKey().euid())))
			.map(o -> o);

		// TODO all? proposals should be direct
		List<Observable<Vertex>> correctProposals = allNodes.stream()
			.map(ECKeyPair::euid)
			.map(bftNetwork.getUnderlyingNetwork()::getNetworkRx)
			.map(EventCoordinatorNetworkRx::consensusMessages)
			.map(p -> p.ofType(Proposal.class).map(Proposal::getVertex))
			.collect(Collectors.toList());
		Observable<Object> directProposalsCheck = Observable.merge(correctProposals)
			.doOnNext(v -> assertThat(v)
				.satisfies(new Condition<>(vtx -> vtx.getView().equals(vtx.getParentView().next()),
					"Vertex %s at %s has direct parent",
					bftNetwork.getProposerElection().getProposer(v.getParentView()).euid(), v.getParentView())))
			.map(o -> o);

		List<Observable<Object>> checks = Arrays.asList(
			correctCommitCheck, progressCheck, correctTimeoutCheck, directProposalsCheck
		);
		Observable.mergeArray(bftNetwork.processBFT(), Observable.merge(checks))
			.take(time, timeUnit)
			.blockingSubscribe();
	}
}
