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
import com.google.common.collect.ImmutableMap;
import com.radixdlt.api.InfoRx;
import com.radixdlt.consensus.bft.BFTBuilder;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.ConsensusRunner;
import com.radixdlt.consensus.ConsensusRunner.Event;
import com.radixdlt.consensus.ConsensusRunner.EventType;
import com.radixdlt.consensus.epoch.EmptySyncVerticesRPCSender;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.EpochChangeRx;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.middleware2.InternalMessagePasser;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.SyncedStateComputer;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.VertexStoreFactory;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeoutSender;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.Hash;

import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.consensus.simulation.network.SimulationNetwork.SimulatedNetworkReceiver;
import com.radixdlt.consensus.simulation.network.SimulationNetwork.SimulationSyncSender;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.radixdlt.utils.ThreadFactories.daemonThreads;

/**
 * A multi-node bft test network where the network and latencies of each message is simulated.
 */
public class SimulationNodes {
	private final int pacemakerTimeout;
	private final SimulationNetwork underlyingNetwork;
	private final ImmutableMap<BFTNode, SystemCounters> counters;
	private final ImmutableMap<BFTNode, InternalMessagePasser> internalMessages;
	private final ImmutableList<ConsensusRunner> runners;
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
		this.counters = nodes.stream().collect(ImmutableMap.toImmutableMap(e -> e, e -> new SystemCountersImpl()));
		this.internalMessages = nodes.stream().collect(ImmutableMap.toImmutableMap(e -> e, e -> new InternalMessagePasser()));
		this.runners = nodes.stream().map(this::createBFTInstance).collect(ImmutableList.toImmutableList());
	}

	private ConsensusRunner createBFTInstance(BFTNode self) {
		final NextCommandGenerator nextCommandGenerator = (view, aids) -> null;
		final Hasher nullHasher = o -> Hash.ZERO_HASH;
		final HashSigner nullSigner = h -> new ECDSASignature();
		final BFTFactory bftFactory =
			(endOfEpochSender, pacemaker, vertexStore, proposerElection, validatorSet, bftInfoSender) ->
				BFTBuilder.create()
					.self(self)
					.endOfEpochSender(endOfEpochSender)
					.pacemaker(pacemaker)
					.nextCommandGenerator(nextCommandGenerator)
					.vertexStore(vertexStore)
					.proposerElection(proposerElection)
					.validatorSet(validatorSet)
					.eventSender(underlyingNetwork.getNetworkSender(self))
					.counters(counters.get(self))
					.infoSender(bftInfoSender)
					.timeSupplier(System::currentTimeMillis)
					.hasher(nullHasher)
					.signer(nullSigner)
					.verifyAuthors(false)
					.build();

		final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(daemonThreads("TimeoutSender"));
		final ScheduledLocalTimeoutSender timeoutSender = new ScheduledLocalTimeoutSender(scheduledExecutorService);
		final SimulationSyncSender syncSender = underlyingNetwork.getSyncSender(self);
		final VertexStoreFactory vertexStoreFactory = (v, qc, stateComputer) ->
			new VertexStore(
				v,
				qc,
				stateComputer,
				getVerticesRPCEnabled ? syncSender : EmptySyncVerticesRPCSender.INSTANCE,
				this.internalMessages.get(self),
				this.counters.get(self)
			);
		final SimulatedStateComputer stateComputer = stateComputerSupplier.get();
		final EpochManager epochManager = new EpochManager(
			self,
			stateComputer,
			syncSender,
			timeoutSender,
			timeoutSender1 -> new FixedTimeoutPacemaker(this.pacemakerTimeout, timeoutSender1),
			vertexStoreFactory,
			proposers -> new WeightedRotatingLeaders(proposers, Comparator.comparing(v -> v.getNode().getKey().euid()), 5),
			bftFactory,
			counters.get(self),
			internalMessages.get(self)
		);

		final SimulatedNetworkReceiver rx = underlyingNetwork.getNetworkRx(self);

		return new ConsensusRunner(
			stateComputer,
			rx,
			timeoutSender,
			internalMessages.get(self),
			Observable::never,
			rx,
			rx,
			epochManager
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
		List<Completable> startedList = this.runners.stream()
			.map(ConsensusRunner::events)
			.map(o -> o.map(Event::getEventType)
				.filter(e -> e.equals(EventType.EPOCH_CHANGE))
				.firstOrError()
				.ignoreElement()
			).collect(Collectors.toList());

		Completable.merge(startedList).subscribe(completableSubject::onComplete);

		this.runners.forEach(ConsensusRunner::start);

		return completableSubject.toSingle(() -> new RunningNetwork() {
			@Override
			public List<BFTNode> getNodes() {
				return nodes;
			}

			@Override
			public InfoRx getInfo(BFTNode node) {
				return internalMessages.get(node);
			}

			@Override
			public SimulationNetwork getUnderlyingNetwork() {
				return underlyingNetwork;
			}
		});
	}

	public void stop() {
		this.runners.forEach(ConsensusRunner::stop);
	}
}
