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

import static org.assertj.core.api.AssertionsForClassTypes.within;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import io.reactivex.rxjava3.core.Observable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.api.Condition;
import org.junit.Test;

/**
 * Tests with networks with crashed nodes
 */
public class CrashFaultNetworkTest {
	static List<ECKeyPair> createNodes(int numNodes, String designation) {
		return Stream.generate(() -> {
			try {
				ECKeyPair keyPair = null;
				do {
					keyPair = new ECKeyPair();
				} while (!keyPair.getUID().toString().startsWith(designation));
				return keyPair;
			} catch (CryptoException e) {
				throw new RuntimeException();
			}
		}).limit(numNodes).collect(Collectors.toList());
	}

	@Test
	public void given_2_out_of_3_correct_bft_instances__then_all_instances_should_only_get_genesis_commit_over_1_minute() {
		final int numNodes = 3;
		final long time = 1;
		final TimeUnit timeUnit = TimeUnit.MINUTES;

		final List<ECKeyPair> nodes = createNodes(numNodes, "");
		final BFTTestNetwork bftNetwork = new BFTTestNetwork(nodes);
		bftNetwork.getTestEventCoordinatorNetwork().setSendingDisable(nodes.get(2).getUID(), true);


		List<Observable<Vertex>> committedObservables = nodes.stream()
			.map(bftNetwork::getVertexStore)
			.map(VertexStore::lastCommittedVertex)
			.collect(Collectors.toList());

		Observable<Vertex> committed = Observable.zip(committedObservables, Arrays::stream)
			.map(s -> s.distinct().collect(Collectors.toList()))
			.take(time, timeUnit)
			.singleOrError()
			.doAfterSuccess(s -> assertThat(s).hasSize(1))
			.map(s -> (Vertex) s.get(0))
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
		faultyNodes.forEach(node -> bftNetwork.getTestEventCoordinatorNetwork().setSendingDisable(node.getUID(), true));
		faultyNodes.forEach(node -> bftNetwork.getTestEventCoordinatorNetwork().setReceivingDisable(node.getUID(), true));

		// correct nodes should all get the same commits in the same order
		Observable<Object> correctCommitCheck = Observable.zip(correctNodes.stream()
			.map(bftNetwork::getVertexStore)
			.map(VertexStore::lastCommittedVertex)
			.collect(Collectors.toList()), Arrays::stream)
			.map(s -> s.distinct().collect(Collectors.toList()))
			.doOnNext(s -> assertThat(s).hasSize(1))
			.map(s -> (Vertex) s.get(0))
			.map(o -> o);

		// correct nodes should not get any timeouts since a quorum can still be formed
		Observable<Object> correctTimeoutCheck = Observable.interval(1, TimeUnit.SECONDS)
			.flatMapIterable(i -> correctNodes)
			.map(bftNetwork::getCounters)
			.doOnNext(counters -> assertThat(counters.getCount(Counters.CounterType.TIMEOUT))
				.satisfies(new Condition<>(c -> c == 0, "Timeout counter is zero.")))
			.map(o -> o);

		// correct proposals should be direct if generated after another correct proposal, otherwise there should be a gap
		List<Observable<Vertex>> correctProposals = correctNodes.stream()
			.map(ECKeyPair::getUID)
			.map(bftNetwork.getTestEventCoordinatorNetwork()::getNetworkRx)
			.map(EventCoordinatorNetworkRx::proposalMessages)
			.collect(Collectors.toList());
		Observable<Object> directProposalsCheck = Observable.merge(correctProposals)
			.filter(v -> correctNodesPubs.contains(bftNetwork.getProposerElection().getProposer(v.getParentView())))
			.doOnNext(v -> assertThat(v)
				.satisfies(new Condition<>(vtx -> vtx.getView().equals(vtx.getParentView().next()), "Vertex after correct has direct parent")))
			.map(o -> o);
		Observable<Object> gapProposalsCheck = Observable.merge(correctProposals)
			.filter(v -> !correctNodesPubs.contains(bftNetwork.getProposerElection().getProposer(v.getParentView())))
			.doOnNext(v -> assertThat(v)
				.satisfies(new Condition<>(vtx -> !vtx.getView().equals(vtx.getParentView().next()), "Vertex after timeout has gap")))
			.map(o -> o);

		Observable.mergeArray(bftNetwork.processBFT(), correctCommitCheck, correctTimeoutCheck, directProposalsCheck, gapProposalsCheck)
			.take(time, timeUnit)
			.blockingSubscribe();
	}

}
