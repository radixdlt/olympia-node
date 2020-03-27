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
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import io.reactivex.rxjava3.core.Observable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * Tests with networks with crashed nodes
 */
public class CrashFaultNetworkTest {
	static List<ECKeyPair> createNodes(int numNodes) {
		return Stream.generate(() -> {
			try {
				return new ECKeyPair();
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

		final List<ECKeyPair> nodes = createNodes(numNodes);
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
}
