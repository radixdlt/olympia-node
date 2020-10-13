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

package com.radixdlt.integration.distributed.simulation;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork.LatencyProvider;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class OneOutOfBoundsLatencyModule extends AbstractModule {
	private final int outOfBoundsLatency;
	private final int inBoundsLatency;

	public OneOutOfBoundsLatencyModule(int inBoundsLatency, int outOfBoundsLatency) {
		this.inBoundsLatency = inBoundsLatency;
		this.outOfBoundsLatency = outOfBoundsLatency;
	}

	@Provides
	@Singleton
	@Named("base")
	LatencyProvider base(ImmutableList<BFTNode> nodes) {
		Map<BFTNode, Integer> nodeLatencies = IntStream.range(0, nodes.size())
			.boxed()
			.collect(Collectors.toMap(nodes::get, i -> i == 0 ? outOfBoundsLatency : inBoundsLatency));
		return msg -> Math.max(nodeLatencies.get(msg.getSender()), nodeLatencies.get(msg.getReceiver()));
	}
}
