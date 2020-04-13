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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECKeyPair;
import io.reactivex.rxjava3.core.Observable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.Condition;
import org.junit.Test;

/**
 * Tests with a perfect network
 */
public class PerfectNetworkTest {
	private static List<ECKeyPair> createNodes(int numNodes) {
		return Stream.generate(ECKeyPair::generateNew).limit(numNodes).collect(Collectors.toList());
	}

	/**
	 * Sanity test check for a perfect network. 4 is the size used because it is
	 * the smallest network size where quorum size (3) != network size. The sanity checks
	 * done are:
	 * 1. Committed vertices are the same across nodes
	 * 2. The size of vertex store does not increase for any node
	 * 3. A timeout never occurs for any node
	 * 4. Every proposal has a direct parent
	 */
	@Test
	public void given_4_correct_bfts__then_should_pass_sanity_tests_over_1_minute() {
		final int numNodes = 4;
		final long time = 1;
		final TimeUnit timeUnit = TimeUnit.MINUTES;

		final List<ECKeyPair> nodes = createNodes(numNodes);
		final BFTTestNetwork bftNetwork = new BFTTestNetwork(nodes);

		// Check that every node agrees to the order of committed vertices
		Observable<Object> commitCheck = Observable.zip(nodes.stream()
			.map(bftNetwork::getVertexStore)
			.map(VertexStore::lastCommittedVertex)
			.collect(Collectors.toList()), Arrays::stream)
			.map(s -> s.distinct().collect(Collectors.toList()))
			.doOnNext(s -> assertThat(s).hasSize(1))
			.map(s -> (Vertex) s.get(0))
			.map(o -> o);

		// Every vertex store should only ever at most have 4 vertices since every vertex
		// should get committed and thus pruned in a perfect network
		Observable<Object> vertexStoreCheck = Observable.timer(2, TimeUnit.SECONDS)
			.map(l -> nodes.stream().map(bftNetwork::getVertexStore).collect(Collectors.toList()))
			.doOnNext(s -> {
				for (VertexStore vertexStore : s) {
					assertThat(vertexStore.getSize()).isLessThanOrEqualTo(4);
				}
			})
			.map(o -> o);

		// Check that no node ever times out
		Observable<Object> timeoutCheck = Observable.interval(2, TimeUnit.SECONDS)
			.flatMapIterable(i -> nodes)
			.map(bftNetwork::getCounters)
			.doOnNext(counters -> {
				assertThat(counters.get(CounterType.CONSENSUS_TIMEOUT))
					.satisfies(new Condition<>(c -> c == 0, "Timeout counter is zero."));
				assertThat(counters.get(CounterType.CONSENSUS_REJECTED))
					.satisfies(new Condition<>(c -> c == 0, "Rejected Proposal counter is zero."));
			})
			.map(o -> o);

		// Check that every received proposal has a direct parent
		List<Observable<Vertex>> proposals = nodes.stream()
			.map(ECKeyPair::euid)
			.map(bftNetwork.getUnderlyingNetwork()::getNetworkRx)
			.map(EventCoordinatorNetworkRx::proposalMessages)
			.collect(Collectors.toList());
		Observable<Object> proposalsCheck = Observable.merge(proposals)
			.doOnNext(v -> assertThat(v)
				.satisfies(new Condition<>(Vertex::hasDirectParent, "Vertex has direct parent")))
			.map(o -> o);

		List<Observable<Object>> checks = Arrays.asList(
			commitCheck, vertexStoreCheck, timeoutCheck, proposalsCheck
		);

		Observable.merge(bftNetwork.processBFT(), Observable.merge(checks))
			.take(time, timeUnit)
			.blockingSubscribe();
	}

}
