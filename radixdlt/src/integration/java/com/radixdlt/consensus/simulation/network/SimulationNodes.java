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

package com.radixdlt.consensus.simulation.network;

import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.radixdlt.ConsensusModule;
import com.radixdlt.SystemInfoMessagesModule;
import com.radixdlt.consensus.simulation.NullCryptoModule;
import com.radixdlt.consensus.simulation.SimulationSyncerAndNetworkModule;
import com.radixdlt.systeminfo.InfoRx;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.ConsensusRunner;
import com.radixdlt.consensus.ConsensusRunner.Event;
import com.radixdlt.consensus.ConsensusRunner.EventType;
import com.radixdlt.consensus.EpochChangeRx;
import com.radixdlt.consensus.SyncedStateComputer;

import com.radixdlt.middleware2.CommittedAtom;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
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
	private final Supplier<SimulatedStateComputer> stateComputerSupplier;

	public interface SimulatedStateComputer extends SyncedStateComputer<CommittedAtom>, EpochChangeRx {
	}

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
		Supplier<SimulatedStateComputer> stateComputerSupplier,
		boolean getVerticesRPCEnabled
	) {
		this.nodes = nodes;
		this.stateComputerSupplier = stateComputerSupplier;
		this.getVerticesRPCEnabled = getVerticesRPCEnabled;
		this.underlyingNetwork = Objects.requireNonNull(underlyingNetwork);
		this.pacemakerTimeout = pacemakerTimeout;
		this.nodeInstances = nodes.stream().map(this::createBFTInstance).collect(ImmutableList.toImmutableList());
	}

	private Injector createBFTInstance(BFTNode self) {
		return Guice.createInjector(
			new ConsensusModule(pacemakerTimeout),
			new SystemInfoMessagesModule(),
			new NullCryptoModule(),
			new SimulationSyncerAndNetworkModule(getVerticesRPCEnabled, self, underlyingNetwork, stateComputerSupplier.get())
		);
	}

	// TODO: Add support for epoch changes
	public interface RunningNetwork {
		List<BFTNode> getNodes();

		InfoRx getInfo(BFTNode node);

		SimulationNetwork getUnderlyingNetwork();
	}

	public Single<RunningNetwork> start() {
		// Send start event once all nodes have reached real epoch event
		final CompletableSubject completableSubject = CompletableSubject.create();
		List<Completable> startedList = this.nodeInstances.stream()
			.map(i -> i.getInstance(ConsensusRunner.class))
			.map(ConsensusRunner::events)
			.map(o -> o.map(Event::getEventType)
				.filter(e -> e.equals(EventType.EPOCH_CHANGE))
				.firstOrError()
				.ignoreElement()
			).collect(Collectors.toList());

		Completable.merge(startedList).subscribe(completableSubject::onComplete);

		this.nodeInstances.forEach(i -> i.getInstance(ConsensusRunner.class).start());

		return completableSubject.toSingle(() -> new RunningNetwork() {
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
		});
	}

	public void stop() {
		this.nodeInstances.forEach(i -> i.getInstance(ConsensusRunner.class).stop());
	}
}
