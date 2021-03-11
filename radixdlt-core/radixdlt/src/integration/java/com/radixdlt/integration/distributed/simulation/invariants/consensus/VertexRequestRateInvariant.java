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

package com.radixdlt.integration.distributed.simulation.invariants.consensus;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.NodeEvents;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.core.Observable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Checks that vertex request explosions don't occur
 */
public final class VertexRequestRateInvariant implements TestInvariant {
	private final NodeEvents nodeEvents;
	private final int permitsPerSecond;

	public VertexRequestRateInvariant(NodeEvents nodeEvents, int permitsPerSecond) {
		this.nodeEvents = nodeEvents;
		this.permitsPerSecond = permitsPerSecond;
	}

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		return Observable.<Pair<BFTNode, GetVerticesRequest>>create(emitter ->
			nodeEvents.addListener((node, req) -> emitter.onNext(Pair.of(node, req)), GetVerticesRequest.class))
			.serialize()
			.groupBy(Pair::getFirst)
			.flatMap(o -> o.buffer(1, TimeUnit.SECONDS)
				.filter(l -> l.size() > permitsPerSecond)
				.map(l -> new TestInvariantError(
					String.format("Get Vertices over the rate limit (%s/sec) for node: %s buffer: %s",
						permitsPerSecond,
						o.getKey(),
						l.stream().collect(Collectors.groupingBy(Pair::getSecond, Collectors.counting())
					))
				))
			);
	}

}
