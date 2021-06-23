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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Modules;
import com.radixdlt.ModuleRunner;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.integration.distributed.simulation.NodeNetworkMessagesModule;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.qualifier.LocalSigner;
import com.radixdlt.utils.Pair;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.reactivex.rxjava3.core.Observable;

import static java.util.function.Predicate.not;

/**
 * A multi-node bft test network where the network and latencies of each message is simulated.
 */
public class SimulationNodes {
	private final SimulationNetwork underlyingNetwork;
	private final ImmutableList<Injector> nodeInstances;
	private final Module baseModule;
	private final Module overrideModule;
	private final Map<ECKeyPair, Module> byzantineNodeModules;

	/**
	 * Create a BFT test network with an underlying simulated network.
	 * @param nodes The nodes on the network
	 * @param underlyingNetwork the network simulator
	 */
	public SimulationNodes(
		List<ECKeyPair> nodes,
		SimulationNetwork underlyingNetwork,
		Module baseModule,
		Module overrideModule,
		Map<ECKeyPair, Module> byzantineNodeModules
	) {
		this.baseModule = baseModule;
		this.overrideModule = overrideModule;
		this.byzantineNodeModules = byzantineNodeModules;
		this.underlyingNetwork = Objects.requireNonNull(underlyingNetwork);
		this.nodeInstances = nodes.stream().map(this::createBFTInstance).collect(ImmutableList.toImmutableList());
	}

	private Injector createBFTInstance(ECKeyPair self) {
		Module module = Modules.combine(
			new AbstractModule() {
				@Provides
				@Self
				private BFTNode self() {
					return BFTNode.create(self.getPublicKey());
				}

				@Provides
				@Self
				private ECPublicKey key() {
					return self.getPublicKey();
				}

				@Provides
				@LocalSigner
				HashSigner hashSigner() {
					return self::sign;
				}

			},
			new NodeNetworkMessagesModule(underlyingNetwork),
			baseModule
		);

		// Override modules can be used to prove that certain adversaries
		// can break network behavior if incorrect modules are used
		if (overrideModule != null) {
			module = Modules.override(module).with(overrideModule);
		}

		Module byzantineModule = byzantineNodeModules.get(self);
		if (byzantineModule != null) {
			module = Modules.override(module).with(byzantineModule);
		}

		return Guice.createInjector(module);
	}

	// TODO: Add support for epoch changes
	public interface RunningNetwork {
		List<BFTNode> getNodes();

		BFTConfiguration bftConfiguration();

		Observable<EpochChange> latestEpochChanges();

		Observable<Pair<BFTNode, LedgerUpdate>> ledgerUpdates();

		<T> EventDispatcher<T> getDispatcher(Class<T> eventClass, BFTNode node);

		<T> T getInstance(Class<T> clazz, BFTNode node);

		<T> T getInstance(Key<T> clazz, BFTNode node);

		SimulationNetwork getUnderlyingNetwork();

		Map<BFTNode, SystemCounters> getSystemCounters();

		void runModule(int nodeIndex, String name);
	}

	public RunningNetwork start(ImmutableMap<Integer, ImmutableSet<String>> disabledModuleRunners) {
		final var moduleRunnersPerNode =
			IntStream.range(0, this.nodeInstances.size())
				.mapToObj(i -> {
					final var injector = this.nodeInstances.get(i);
					final var moduleRunners =
						injector.getInstance(Key.get(new TypeLiteral<Map<String, ModuleRunner>>() { }));
					return Pair.of(i, moduleRunners);
				})
				.collect(ImmutableList.toImmutableList());

		for (var pair : moduleRunnersPerNode) {
			final var nodeDisabledModuleRunners =
				disabledModuleRunners.getOrDefault(pair.getFirst(), ImmutableSet.of());

			pair.getSecond().entrySet().stream()
				.filter(not(e -> nodeDisabledModuleRunners.contains(e.getKey())))
				.forEach(e -> e.getValue().start());
		}

		final List<BFTNode> bftNodes = this.nodeInstances.stream()
			.map(i -> i.getInstance(Key.get(BFTNode.class, Self.class)))
			.collect(Collectors.toList());

		return new RunningNetwork() {
			@Override
			public List<BFTNode> getNodes() {
				return bftNodes;
			}

			@Override
			public BFTConfiguration bftConfiguration() {
				return nodeInstances.get(0).getInstance(BFTConfiguration.class);
			}

			@Override
			public Observable<EpochChange> latestEpochChanges() {
				// Just do first instance for now
				EpochChange initialEpoch =  nodeInstances.get(0).getInstance(EpochChange.class);

				Set<Observable<EpochChange>> epochChanges = nodeInstances.stream()
					.map(i -> i.getInstance(Key.get(new TypeLiteral<Observable<EpochsLedgerUpdate>>() { })))
					.map(o -> o.filter(u -> u.getEpochChange().isPresent()).map(u -> u.getEpochChange().get()))
					.collect(Collectors.toSet());

				return Observable.just(initialEpoch).concatWith(
					Observable.merge(epochChanges)
						.scan((cur, next) -> next.getProof().getEpoch() > cur.getProof().getEpoch() ? next : cur)
						.distinctUntilChanged()
				);
			}

			@Override
			public Observable<Pair<BFTNode, LedgerUpdate>> ledgerUpdates() {
				Set<Observable<Pair<BFTNode, LedgerUpdate>>> committedCommands = nodeInstances.stream()
					.map(i -> {
						BFTNode node = i.getInstance(Key.get(BFTNode.class, Self.class));
						return i.getInstance(Key.get(new TypeLiteral<Observable<LedgerUpdate>>() { }))
							.map(v -> Pair.of(node, v));
					})
					.collect(Collectors.toSet());

				return Observable.merge(committedCommands);
			}

			@Override
			public <T> EventDispatcher<T> getDispatcher(Class<T> eventClass, BFTNode node) {
				return getInstance(Environment.class, node).getDispatcher(eventClass);
			}

			@Override
			public <T> T getInstance(Class<T> clazz, BFTNode node) {
				return nodeInstances.get(getNodes().indexOf(node)).getInstance(clazz);
			}

			@Override
			public <T> T getInstance(Key<T> clazz, BFTNode node) {
				return nodeInstances.get(getNodes().indexOf(node)).getInstance(clazz);
			}

			@Override
			public SimulationNetwork getUnderlyingNetwork() {
				return underlyingNetwork;
			}

			@Override
			public Map<BFTNode, SystemCounters> getSystemCounters() {
				return bftNodes.stream()
					.collect(Collectors.toMap(
						node -> node,
						node -> nodeInstances.get(bftNodes.indexOf(node)).getInstance(SystemCounters.class)
					));
			}

			@Override
			public void runModule(int nodeIndex, String name) {
				nodeInstances.get(nodeIndex)
					.getInstance(Key.get(new TypeLiteral<Map<String, ModuleRunner>>() { }))
					.get(name)
					.start();
			}
		};
	}

	public void stop() {
		this.nodeInstances.stream()
			.flatMap(i -> i.getInstance(Key.get(new TypeLiteral<Map<String, ModuleRunner>>() { })).values().stream())
			.forEach(ModuleRunner::stop);
	}
}
