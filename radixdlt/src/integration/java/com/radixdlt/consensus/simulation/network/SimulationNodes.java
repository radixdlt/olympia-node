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

import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.BFTEventReducer;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.ConsensusRunner;
import com.radixdlt.consensus.ConsensusRunner.Event;
import com.radixdlt.consensus.ConsensusRunner.EventType;
import com.radixdlt.consensus.DefaultHasher;
import com.radixdlt.consensus.EmptySyncVerticesRPCSender;
import com.radixdlt.consensus.EpochManager;
import com.radixdlt.consensus.EpochChangeRx;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.InternalMessagePasser;
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.SyncedStateComputer;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.VertexStoreEventsRx;
import com.radixdlt.consensus.VertexStoreFactory;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker;
import com.radixdlt.consensus.liveness.MempoolProposalGenerator;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.ScheduledTimeoutSender;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.mempool.EmptyMempool;
import com.radixdlt.mempool.Mempool;

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
	private final ImmutableMap<ECKeyPair, SystemCounters> counters;
	private final ImmutableMap<ECKeyPair, InternalMessagePasser> internalMessages;
	private final ImmutableMap<ECKeyPair, ConsensusRunner> runners;
	private final List<ECKeyPair> nodes;
	private final boolean getVerticesRPCEnabled;
	private final Supplier<SimulatedStateComputer> stateComputerSupplier;

	public interface SimulatedStateComputer extends SyncedStateComputer<CommittedAtom>, EpochChangeRx {
	}

	/**
	 * Create a BFT test network with an underlying simulated network.
	 * @param nodes The nodes to populate the network with
	 * @param underlyingNetwork the network simulator
	 * @param pacemakerTimeout a fixed pacemaker timeout used for all nodes
	 */
	public SimulationNodes(
		List<ECKeyPair> nodes,
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
		this.runners = nodes.stream().collect(ImmutableMap.toImmutableMap(e -> e, this::createBFTInstance));
	}

	private ConsensusRunner createBFTInstance(ECKeyPair key) {
		final Mempool mempool = new EmptyMempool();
		final Hasher hasher = new DefaultHasher();
		final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(daemonThreads("TimeoutSender"));
		final ScheduledTimeoutSender timeoutSender = new ScheduledTimeoutSender(scheduledExecutorService);
		final SimulationSyncSender syncSender = underlyingNetwork.getSyncSender(key.getPublicKey());

		final VertexStoreFactory vertexStoreFactory = (v, qc, stateComputer) ->
			new VertexStore(
				v,
				qc,
				stateComputer,
				getVerticesRPCEnabled ? syncSender : EmptySyncVerticesRPCSender.INSTANCE,
				this.internalMessages.get(key),
				this.counters.get(key)
			);

		final SimulatedStateComputer stateComputer = stateComputerSupplier.get();
		BFTFactory bftFactory =
			(endOfEpochSender, pacemaker, vertexStore, proposerElection, validatorSet) -> {
				final ProposalGenerator proposalGenerator = new MempoolProposalGenerator(vertexStore, mempool);
				final SafetyRules safetyRules = new SafetyRules(key, SafetyState.initialState(), hasher);
				final PendingVotes pendingVotes = new PendingVotes(hasher);

				return new BFTEventReducer(
					proposalGenerator,
					mempool,
					underlyingNetwork.getNetworkSender(key.getPublicKey()),
					endOfEpochSender,
					safetyRules,
					pacemaker,
					vertexStore,
					pendingVotes,
					proposerElection,
					key,
					validatorSet,
					counters.get(key)
				);
			};

		final String loggerPrefix = key.euid().toString().substring(0, 6);

		final EpochManager epochManager = new EpochManager(
			loggerPrefix,
			stateComputer,
			syncSender,
			timeoutSender,
			timeoutSender1 -> new FixedTimeoutPacemaker(this.pacemakerTimeout, timeoutSender1),
			vertexStoreFactory,
			proposers -> new WeightedRotatingLeaders(proposers, Comparator.comparing(v -> v.nodeKey().euid()), 5),
			bftFactory,
			key.getPublicKey(),
			counters.get(key)
		);

		final SimulatedNetworkReceiver rx = underlyingNetwork.getNetworkRx(key.getPublicKey());

		return new ConsensusRunner(
			stateComputer,
			rx,
			timeoutSender,
			internalMessages.get(key),
			Observable::never,
			rx,
			rx,
			epochManager
		);
	}

	// TODO: Add support for epoch changes
	public interface RunningNetwork {
		List<ECKeyPair> getNodes();

		VertexStoreEventsRx getVertexStoreEvents(ECKeyPair keyPair);

		SystemCounters getCounters(ECKeyPair keyPair);

		SimulationNetwork getUnderlyingNetwork();
	}

	public Single<RunningNetwork> start() {
		// Send start event once all nodes have reached real epoch event
		final CompletableSubject completableSubject = CompletableSubject.create();
		List<Completable> startedList = this.runners.values().stream()
			.map(ConsensusRunner::events)
			.map(o -> o.map(Event::getEventType)
				.filter(e -> e.equals(EventType.EPOCH_CHANGE))
				.firstOrError()
				.ignoreElement()
			).collect(Collectors.toList());

		Completable.merge(startedList).subscribe(completableSubject::onComplete);

		this.runners.values().forEach(ConsensusRunner::start);

		return completableSubject.toSingle(() -> new RunningNetwork() {
			@Override
			public List<ECKeyPair> getNodes() {
				return nodes;
			}

			@Override
			public VertexStoreEventsRx getVertexStoreEvents(ECKeyPair keyPair) {
				return internalMessages.get(keyPair);
			}

			@Override
			public SystemCounters getCounters(ECKeyPair keyPair) {
				return counters.get(keyPair);
			}

			@Override
			public SimulationNetwork getUnderlyingNetwork() {
				return underlyingNetwork;
			}
		});
	}

	public void stop() {
		this.runners.values().forEach(ConsensusRunner::stop);
	}
}
