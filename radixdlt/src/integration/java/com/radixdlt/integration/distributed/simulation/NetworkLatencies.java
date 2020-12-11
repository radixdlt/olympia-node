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
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.integration.distributed.simulation.network.RandomLatencyProvider;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork;
import com.radixdlt.integration.distributed.simulation.network.LatencyProvider;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class NetworkLatencies {
	public static Module fixed(int latency) {
		return new AbstractModule() {
			@Provides
			@Named("base")
			LatencyProvider base() {
				return msg -> latency;
			}
		};
	}

	public static Module fixed() {
		return fixed(SimulationNetwork.DEFAULT_LATENCY);
	}

	public static Module random(int minLatency, int maxLatency) {
		return new AbstractModule() {
			@Provides
			@Singleton
			@Named("base")
			LatencyProvider base() {
				return new RandomLatencyProvider(minLatency, maxLatency);
			}
		};
	}

	public static Module oneSlowProposalSender(int inBoundsLatency, int outOfBoundsLatency) {
		return new AbstractModule() {
			@Provides
			@Singleton
			@Named("base")
			LatencyProvider base(ImmutableList<BFTNode> nodes) {
				return msg -> {
					if ((msg.getSender().equals(nodes.get(0)) || msg.getReceiver().equals(nodes.get(0)))
						&& (msg.getContent() instanceof Proposal || msg.getContent() instanceof Vote
						|| msg.getContent() instanceof ViewTimeout || msg.getContent() instanceof GetVerticesResponse)
					) {
						return outOfBoundsLatency;
					} else {
						return inBoundsLatency;
					}
				};
			}
		};
	}

	public static Module oneOutOfBounds(int inBoundsLatency, int outOfBoundsLatency) {
		return new AbstractModule() {
			@Provides
			@Singleton
			@Named("base")
			LatencyProvider base(ImmutableList<BFTNode> nodes) {
				Map<BFTNode, Integer> nodeLatencies = IntStream.range(0, nodes.size())
					.boxed()
					.collect(Collectors.toMap(nodes::get, i -> i == 0 ? outOfBoundsLatency : inBoundsLatency));
				return msg -> Math.max(nodeLatencies.get(msg.getSender()), nodeLatencies.get(msg.getReceiver()));
			}
		};
	}

	private NetworkLatencies() {
		throw new UnsupportedOperationException("Cannot instantiate.");
	}
}
