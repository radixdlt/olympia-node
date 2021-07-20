/* Copyright 2021 Radix DLT Ltd incorporated in England.
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
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
					.map(i -> i.getInstance(Key.get(new TypeLiteral<Observable<LedgerUpdate>>() { })))
					.map(o -> o
						.map(u -> u.getStateComputerOutput().getInstance(EpochChange.class))
						.filter(Objects::nonNull)
					)
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
				int index = getNodes().indexOf(node);
				return nodeInstances.get(index).getInstance(Environment.class).getDispatcher(eventClass);
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
