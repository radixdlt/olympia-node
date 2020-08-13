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

package com.radixdlt.integration.distributed.simulation.network;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.radixdlt.ConsensusModule;
import com.radixdlt.SystemInfoMessagesModule;
import com.radixdlt.integration.distributed.simulation.MockedCryptoModule;
import com.radixdlt.integration.distributed.simulation.SimulationNetworkModule;
import com.radixdlt.systeminfo.InfoRx;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.ConsensusRunner;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A multi-node bft test network where the network and latencies of each message is simulated.
 */
public class SimulationNodes {
	private final int pacemakerTimeout;
	private final SimulationNetwork underlyingNetwork;
	private final ImmutableList<Injector> nodeInstances;
	private final List<BFTNode> nodes;
	private final boolean getVerticesRPCEnabled;
	private final ImmutableList<Module> syncModules;

	/**
	 * Create a BFT test network with an underlying simulated network.
	 * @param nodes The nodes on the network
	 * @param underlyingNetwork the network simulator
	 * @param pacemakerTimeout a fixed pacemaker timeout used for all nodes
	 */
	public SimulationNodes(
		List<BFTNode> nodes,
		SimulationNetwork underlyingNetwork,
		int pacemakerTimeout,
		ImmutableList<Module> syncModules,
		boolean getVerticesRPCEnabled
	) {
		this.nodes = nodes;
		this.syncModules = syncModules;
		this.getVerticesRPCEnabled = getVerticesRPCEnabled;
		this.underlyingNetwork = Objects.requireNonNull(underlyingNetwork);
		this.pacemakerTimeout = pacemakerTimeout;
		this.nodeInstances = nodes.stream().map(this::createBFTInstance).collect(ImmutableList.toImmutableList());
	}

	private Injector createBFTInstance(BFTNode self) {
		List<Module> modules = ImmutableList.of(
			new ConsensusModule(pacemakerTimeout),
			new SystemInfoMessagesModule(),
			new MockedCryptoModule(),
			new SimulationNetworkModule(getVerticesRPCEnabled, self, underlyingNetwork)
		);
		return Guice.createInjector(Iterables.concat(modules, syncModules));
	}

	// TODO: Add support for epoch changes
	public interface RunningNetwork {
		List<BFTNode> getNodes();

		InfoRx getInfo(BFTNode node);

		SimulationNetwork getUnderlyingNetwork();
	}

	public RunningNetwork start() {

		List<ConsensusRunner> consensusRunners = this.nodeInstances.stream()
			.map(i -> i.getInstance(ConsensusRunner.class))
			.collect(Collectors.toList());

		for (ConsensusRunner consensusRunner : consensusRunners) {
			consensusRunner.start();
		}

		return new RunningNetwork() {
			@Override
			public List<BFTNode> getNodes() {
				return nodes;
			}

			@Override
			public InfoRx getInfo(BFTNode node) {
				int index = nodes.indexOf(node);
				return nodeInstances.get(index).getInstance(InfoRx.class);
			}

			@Override
			public SimulationNetwork getUnderlyingNetwork() {
				return underlyingNetwork;
			}
		};
	}

	public void stop() {
		this.nodeInstances.forEach(i -> i.getInstance(ConsensusRunner.class).stop());
	}
}
