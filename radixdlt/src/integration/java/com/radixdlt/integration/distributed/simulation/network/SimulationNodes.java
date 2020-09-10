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
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.radixdlt.ConsensusModule;
import com.radixdlt.ConsensusRxModule;
import com.radixdlt.ConsensusRunner;
import com.radixdlt.SystemInfoRxModule;
import com.radixdlt.consensus.EpochChangeRx;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.integration.distributed.simulation.MockedCryptoModule;
import com.radixdlt.integration.distributed.simulation.SimulationNetworkModule;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.systeminfo.InfoRx;
import com.radixdlt.consensus.bft.BFTNode;

import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.core.Observable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
			new AbstractModule() {
				@Override
				public void configure() {
					bind(BFTNode.class).annotatedWith(Names.named("self")).toInstance(self);
					bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
					bind(TimeSupplier.class).toInstance(System::currentTimeMillis);
				}
			},
			new ConsensusModule(pacemakerTimeout),
			new ConsensusRxModule(),
			new SystemInfoRxModule(),
			new MockedCryptoModule(),
			new SimulationNetworkModule(getVerticesRPCEnabled, underlyingNetwork)
		);
		return Guice.createInjector(Iterables.concat(modules, syncModules));
	}

	// TODO: Add support for epoch changes
	public interface RunningNetwork {
		List<BFTNode> getNodes();

		Observable<EpochChange> latestEpochChanges();

		Observable<Pair<BFTNode, VerifiedVertex>> committedVertices();

		Observable<Pair<BFTNode, VerifiedCommandsAndProof>> committedCommands();

		InfoRx getInfo(BFTNode node);

		Mempool getMempool(BFTNode node);

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
			public Observable<EpochChange> latestEpochChanges() {
				// Just do first instance for now
				EpochChange initialEpoch =  nodeInstances.get(0).getInstance(EpochChange.class);

				Set<Observable<EpochChange>> epochChanges = nodeInstances.stream()
					.map(i -> i.getInstance(EpochChangeRx.class))
					.map(EpochChangeRx::epochChanges)
					.collect(Collectors.toSet());

				return Observable.just(initialEpoch).concatWith(
					Observable.merge(epochChanges)
						.scan((cur, next) -> next.getProof().getEpoch() > cur.getProof().getEpoch() ? next : cur)
						.distinctUntilChanged()
				);
			}

			@Override
			public Observable<Pair<BFTNode, VerifiedVertex>> committedVertices() {
				Set<Observable<Pair<BFTNode, VerifiedVertex>>> committedVertices = nodeInstances.stream()
					.map(i -> {
						BFTNode node = i.getInstance(Key.get(BFTNode.class, Names.named("self")));
						return i.getInstance(InfoRx.class).committedVertices()
							.map(v -> Pair.of(node, v));
					})
					.collect(Collectors.toSet());

				return Observable.merge(committedVertices);
			}

			@Override
			public Observable<Pair<BFTNode, VerifiedCommandsAndProof>> committedCommands() {
				Set<Observable<Pair<BFTNode, VerifiedCommandsAndProof>>> committedCommands = nodeInstances.stream()
					.map(i -> {
						BFTNode node = i.getInstance(Key.get(BFTNode.class, Names.named("self")));
						return i.getInstance(InfoRx.class).committedCommands()
							.map(v -> Pair.of(node, v));
					})
					.collect(Collectors.toSet());

				return Observable.merge(committedCommands);
			}

			@Override
			public InfoRx getInfo(BFTNode node) {
				int index = nodes.indexOf(node);
				return nodeInstances.get(index).getInstance(InfoRx.class);
			}

			@Override
			public Mempool getMempool(BFTNode node) {
				int index = nodes.indexOf(node);
				return nodeInstances.get(index).getInstance(Mempool.class);
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
