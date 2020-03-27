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

import com.radixdlt.consensus.Counters.CounterType;
import com.radixdlt.crypto.CryptoException;
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
		return Stream.generate(() -> {
			try {
				return new ECKeyPair();
			} catch (CryptoException e) {
				throw new RuntimeException();
			}
		}).limit(numNodes).collect(Collectors.toList());
	}

	/**
	 * 4 is the smallest network size where quorum size (3) != network size
	 */
	@Test
	public void given_4_correct_bfts__then_all_should_get_same_commits_consecutive_vertices_and_no_timeouts_over_1_minute() {
		final int numNodes = 4;
		final long time = 1;
		final TimeUnit timeUnit = TimeUnit.MINUTES;

		final List<ECKeyPair> nodes = createNodes(numNodes);
		final BFTTestNetwork bftNetwork = new BFTTestNetwork(nodes);

		Observable<Object> commitCheck = Observable.zip(nodes.stream()
			.map(bftNetwork::getVertexStore)
			.map(VertexStore::lastCommittedVertex)
			.collect(Collectors.toList()), Arrays::stream)
			.map(s -> s.distinct().collect(Collectors.toList()))
			.doOnNext(s -> assertThat(s).hasSize(1))
			.map(s -> (Vertex) s.get(0))
			.map(o -> o);

		Observable<Object> timeoutCheck = Observable.interval(2, TimeUnit.SECONDS)
			.flatMapIterable(i -> nodes)
			.map(bftNetwork::getCounters)
			.doOnNext(counters -> assertThat(counters.getCount(CounterType.TIMEOUT))
				.satisfies(new Condition<>(c -> c == 0, "Timeout counter is zero.")))
			.map(o -> o);

		List<Observable<Vertex>> proposals = nodes.stream()
			.map(ECKeyPair::getUID)
			.map(bftNetwork.getTestEventCoordinatorNetwork()::getNetworkRx)
			.map(EventCoordinatorNetworkRx::proposalMessages)
			.collect(Collectors.toList());

		Observable<Object> proposalsCheck = Observable.merge(proposals)
			.doOnNext(v -> assertThat(v)
				.satisfies(new Condition<>(vtx -> vtx.getView().equals(vtx.getParentView().next()), "Vertex has direct parent")))
			.map(o -> o);

		Observable.merge(bftNetwork.processBFT(), commitCheck, timeoutCheck, proposalsCheck)
			.take(time, timeUnit)
			.blockingSubscribe();
	}
}
