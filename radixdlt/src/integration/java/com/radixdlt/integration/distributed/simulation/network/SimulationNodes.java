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
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Timeout;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.integration.distributed.simulation.NodeNetworkMessagesModule;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.consensus.bft.BFTNode;

import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.core.Observable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

		Observable<Pair<BFTNode, BFTCommittedUpdate>> bftCommittedUpdates();

		Observable<Pair<BFTNode, LedgerUpdate>> ledgerUpdates();

		Observable<Pair<BFTNode, QuorumCertificate>> highQCs();

		Observable<Pair<BFTNode, Timeout>> timeouts();

		Mempool getMempool(BFTNode node);

		SimulationNetwork getUnderlyingNetwork();

		Map<BFTNode, SystemCounters> getSystemCounters();
	}

	public RunningNetwork start() {
		List<ModuleRunner> moduleRunners = this.nodeInstances.stream()
			.flatMap(i -> i.getInstance(Key.get(new TypeLiteral<Map<String, ModuleRunner>>() { })).values().stream())
			.collect(Collectors.toList());

		for (ModuleRunner moduleRunner : moduleRunners) {
			moduleRunner.start();
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
			public Observable<Pair<BFTNode, BFTCommittedUpdate>> bftCommittedUpdates() {
				Set<Observable<Pair<BFTNode, BFTCommittedUpdate>>> committedVertices = nodeInstances.stream()
					.map(i -> {
						BFTNode node = i.getInstance(Key.get(BFTNode.class, Self.class));
						return i.getInstance(Key.get(new TypeLiteral<Observable<BFTCommittedUpdate>>() { }))
							.map(v -> Pair.of(node, v));
					})
					.collect(Collectors.toSet());

				return Observable.merge(committedVertices);
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
			public Observable<Pair<BFTNode, QuorumCertificate>> highQCs() {
				Set<Observable<Pair<BFTNode, QuorumCertificate>>> highQCs = nodeInstances.stream()
					.map(i -> {
						BFTNode node = i.getInstance(Key.get(BFTNode.class, Self.class));
						return i.getInstance(Key.get(new TypeLiteral<Observable<QuorumCertificate>>() { }))
							.map(v -> Pair.of(node, v));
					})
					.collect(Collectors.toSet());

				return Observable.merge(highQCs);
			}

			@Override
			public Observable<Pair<BFTNode, Timeout>> timeouts() {
				Set<Observable<Pair<BFTNode, Timeout>>> timeouts = nodeInstances.stream()
					.map(i -> {
						BFTNode node = i.getInstance(Key.get(BFTNode.class, Self.class));
						return i.getInstance(Key.get(new TypeLiteral<Observable<Timeout>>() { }))
							.map(v -> Pair.of(node, v));
					})
					.collect(Collectors.toSet());

				return Observable.merge(timeouts);
			}

			@Override
			public Mempool getMempool(BFTNode node) {
				int index = getNodes().indexOf(node);
				return nodeInstances.get(index).getInstance(Mempool.class);
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
		};
	}

	public void stop() {
		this.nodeInstances.stream()
			.flatMap(i -> i.getInstance(Key.get(new TypeLiteral<Map<String, ModuleRunner>>() { })).values().stream())
			.forEach(ModuleRunner::stop);
	}
}
