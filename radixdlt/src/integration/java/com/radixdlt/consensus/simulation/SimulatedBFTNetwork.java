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

package com.radixdlt.consensus.simulation;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.BasicEpochChangeRx;
import com.radixdlt.consensus.ConsensusRunner;
import com.radixdlt.consensus.ConsensusRunner.Event;
import com.radixdlt.consensus.ConsensusRunner.EventType;
import com.radixdlt.consensus.DefaultHasher;
import com.radixdlt.consensus.EmptySyncVerticesRPCSender;
import com.radixdlt.consensus.EpochManager;
import com.radixdlt.consensus.EpochChangeRx;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.InternalMessagePasser;
import com.radixdlt.consensus.SyncedStateComputer;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexStore;
import com.radixdlt.consensus.SyncVerticesRPCSender;
import com.radixdlt.consensus.VertexStoreEventsRx;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.liveness.ScheduledTimeoutSender;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.mempool.EmptyMempool;
import com.radixdlt.mempool.Mempool;

import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork;
import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork.SimulatedNetworkReceiver;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.radixdlt.utils.ThreadFactories.daemonThreads;

/**
 * A multi-node bft test network where the network and latencies of each message is simulated.
 */
public class SimulatedBFTNetwork {
	private static final int TEST_PACEMAKER_TIMEOUT = 1000;

	private final int pacemakerTimeout;
	private final TestEventCoordinatorNetwork underlyingNetwork;
	private final ImmutableMap<ECKeyPair, SystemCounters> counters;
	private final ImmutableMap<ECKeyPair, ScheduledTimeoutSender> timeoutSenders;
	private final ImmutableMap<ECKeyPair, InternalMessagePasser> internalMessages;
	private final ImmutableMap<ECKeyPair, FixedTimeoutPacemaker> pacemakers;
	private final ImmutableMap<ECKeyPair, ConsensusRunner> runners;
	private final ConcurrentMap<ECKeyPair, VertexStore> vertexStores;
	private final ConcurrentMap<ECKeyPair, ProposerElection> proposerElections;
	private final List<ECKeyPair> nodes;
	private final boolean getVerticesRPCEnabled;

	/**
	 * Create a BFT test network with an underlying simulated network
	 * @param nodes The nodes to populate the network with
	 * @param underlyingNetwork the network simulator
	 */
	public SimulatedBFTNetwork(List<ECKeyPair> nodes, TestEventCoordinatorNetwork underlyingNetwork) {
		this(nodes, underlyingNetwork, TEST_PACEMAKER_TIMEOUT, true);
	}

	/**
	 * Create a BFT test network with an underlying simulated network.
	 * @param nodes The nodes to populate the network with
	 * @param underlyingNetwork the network simulator
	 * @param pacemakerTimeout a fixed pacemaker timeout used for all nodes
	 */
	public SimulatedBFTNetwork(
		List<ECKeyPair> nodes,
		TestEventCoordinatorNetwork underlyingNetwork,
		int pacemakerTimeout,
		boolean getVerticesRPCEnabled
	) {
		this.nodes = nodes;
		this.getVerticesRPCEnabled = getVerticesRPCEnabled;
		this.underlyingNetwork = Objects.requireNonNull(underlyingNetwork);
		this.pacemakerTimeout = pacemakerTimeout;
		this.counters = nodes.stream().collect(ImmutableMap.toImmutableMap(e -> e, e -> new SystemCountersImpl()));
		this.internalMessages = nodes.stream().collect(ImmutableMap.toImmutableMap(e -> e, e -> new InternalMessagePasser()));
		this.vertexStores = new ConcurrentHashMap<>();
		this.timeoutSenders = nodes.stream().collect(ImmutableMap.toImmutableMap(
			e -> e,
			e -> new ScheduledTimeoutSender(Executors.newSingleThreadScheduledExecutor(daemonThreads("TimeoutSender")))
		));
		this.pacemakers = nodes.stream().collect(ImmutableMap.toImmutableMap(e -> e,
			e -> new FixedTimeoutPacemaker(this.pacemakerTimeout, this.timeoutSenders.get(e))));
		this.runners = nodes.stream().collect(ImmutableMap.toImmutableMap(
			e -> e, this::createBFTInstance));
		this.proposerElections = new ConcurrentHashMap<>();
	}

	private ConsensusRunner createBFTInstance(ECKeyPair key) {
		Mempool mempool = new EmptyMempool();
		Hasher hasher = new DefaultHasher();
		ScheduledTimeoutSender timeoutSender = timeoutSenders.get(key);
		FixedTimeoutPacemaker pacemaker = pacemakers.get(key);
		EpochChangeRx epochChangeRx = new BasicEpochChangeRx(nodes.stream().map(ECKeyPair::getPublicKey).collect(Collectors.toList()));
		EpochManager epochManager = new EpochManager(
			mempool,
			underlyingNetwork.getNetworkSender(key.getPublicKey()),
			() -> pacemaker,
			(v, qc) -> vertexStores.computeIfAbsent(key, keyPair -> {
				SyncedStateComputer<CommittedAtom> stateComputer = new SyncedStateComputer<CommittedAtom>() {
					@Override
					public boolean syncTo(long targetStateVersion, List<ECPublicKey> target, Object opaque) {
						return true;
					}

					@Override
					public void execute(CommittedAtom instruction) {
					}
				};
				SyncVerticesRPCSender syncVerticesRPCSender = getVerticesRPCEnabled
					? underlyingNetwork.getVerticesRequestSender(keyPair.getPublicKey())
					: EmptySyncVerticesRPCSender.INSTANCE;
				return new VertexStore(
					v,
					qc,
					stateComputer,
					syncVerticesRPCSender,
					this.internalMessages.get(keyPair),
					this.counters.get(keyPair)
				);
			}),
			proposers -> proposerElections.computeIfAbsent(key, keyPair ->
				new WeightedRotatingLeaders(proposers, Comparator.comparing(v -> v.nodeKey().euid()), 5)
			), // create a new ProposerElection per node
			hasher,
			key,
			counters.get(key)
		);

		SimulatedNetworkReceiver rx = underlyingNetwork.getNetworkRx(key.getPublicKey());

		return new ConsensusRunner(epochChangeRx,
			rx,
			timeoutSender,
			internalMessages.get(key),
			Observable::never,
			rx,
			epochManager
		);
	}

	// TODO: Add support for epoch changes
	public interface RunningNetwork {
		Vertex getGenesisVertex();

		List<ECKeyPair> getNodes();

		VertexStoreEventsRx getVertexStoreEvents(ECKeyPair keyPair);

		SystemCounters getCounters(ECKeyPair keyPair);

		TestEventCoordinatorNetwork getUnderlyingNetwork();
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
			public Vertex getGenesisVertex() {
				return Vertex.createGenesis();
			}

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
			public TestEventCoordinatorNetwork getUnderlyingNetwork() {
				return underlyingNetwork;
			}
		});
	}

	public void stop() {
		this.runners.values().forEach(ConsensusRunner::stop);
	}
}
