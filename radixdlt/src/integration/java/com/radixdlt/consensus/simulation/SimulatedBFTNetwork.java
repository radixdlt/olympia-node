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
import com.radixdlt.consensus.ConsensusRunner;
import com.radixdlt.consensus.ConsensusRunner.Event;
import com.radixdlt.consensus.ConsensusRunner.EventType;
import com.radixdlt.consensus.DefaultHasher;
import com.radixdlt.consensus.EpochManager;
import com.radixdlt.consensus.EpochRx;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.LocalSyncSender;
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.SyncedStateComputer;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.VertexStore;
import com.radixdlt.consensus.VertexSupplier;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker;
import com.radixdlt.consensus.liveness.MempoolProposalGenerator;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.liveness.ScheduledTimeoutSender;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.mempool.EmptyMempool;
import com.radixdlt.mempool.Mempool;

import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork;
import com.radixdlt.utils.UInt256;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * A multi-node bft test network where the network and latencies of each message is simulated.
 */
public class SimulatedBFTNetwork {
	private static final int TEST_PACEMAKER_TIMEOUT = 1000;

	private final int pacemakerTimeout;
	private final TestEventCoordinatorNetwork underlyingNetwork;
	private final Vertex genesisVertex;
	private final QuorumCertificate genesisQC;
	private final ImmutableMap<ECKeyPair, VertexStore> vertexStores;
	private final ImmutableMap<ECKeyPair, SystemCounters> counters;
	private final ImmutableMap<ECKeyPair, ScheduledTimeoutSender> timeoutSenders;
	private final ImmutableMap<ECKeyPair, LocalSyncSender> syncSenders;
	private final ImmutableMap<ECKeyPair, FixedTimeoutPacemaker> pacemakers;
	private final ImmutableMap<ECKeyPair, ConsensusRunner> runners;
	private final ValidatorSet validatorSet;
	private final List<ECKeyPair> nodes;

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
		this.underlyingNetwork = Objects.requireNonNull(underlyingNetwork);
		this.pacemakerTimeout = pacemakerTimeout;
		this.genesisVertex = Vertex.createGenesis(null);
		this.genesisQC = new QuorumCertificate(
			new VoteData(VertexMetadata.ofVertex(genesisVertex), null, null),
			new ECDSASignatures()
		);
		this.validatorSet = ValidatorSet.from(
			nodes.stream()
				.map(ECKeyPair::getPublicKey)
				.map(pk -> Validator.from(pk, UInt256.ONE))
				.collect(Collectors.toList())
		);
		this.counters = nodes.stream().collect(ImmutableMap.toImmutableMap(e -> e, e -> new SystemCountersImpl()));
		this.syncSenders = nodes.stream().collect(ImmutableMap.toImmutableMap(e -> e, e -> new LocalSyncSender()));
		this.vertexStores = nodes.stream()
			.collect(ImmutableMap.toImmutableMap(
				e -> e,
				e -> {
					SyncedStateComputer<CommittedAtom> stateComputer = new SyncedStateComputer<CommittedAtom>() {
						@Override
						public Completable syncTo(long targetStateVersion, List<ECPublicKey> target) {
							return Completable.complete();
						}

						@Override
						public void execute(CommittedAtom instruction) {
						}
					};
					VertexSupplier vertexSupplier = getVerticesRPCEnabled
						? underlyingNetwork.getVertexSupplier(e.getPublicKey())
						: (hash, node, id) -> Single.error(new UnsupportedOperationException());
					return new VertexStore(genesisVertex, genesisQC, stateComputer, vertexSupplier, this.syncSenders.get(e), this.counters.get(e));
				})
			);
		this.timeoutSenders = nodes.stream().collect(ImmutableMap.toImmutableMap(e -> e,
			e -> new ScheduledTimeoutSender(Executors.newSingleThreadScheduledExecutor())));
		this.pacemakers = nodes.stream().collect(ImmutableMap.toImmutableMap(e -> e,
			e -> new FixedTimeoutPacemaker(this.pacemakerTimeout, this.timeoutSenders.get(e))));
		this.runners = this.vertexStores.keySet().stream()
			.collect(ImmutableMap.toImmutableMap(
				e -> e,
				this::createBFTInstance
			));
	}

	public List<ECKeyPair> getNodes() {
		return nodes;
	}

	private ConsensusRunner createBFTInstance(ECKeyPair key) {
		Mempool mempool = new EmptyMempool();
		ProposalGenerator proposalGenerator = new MempoolProposalGenerator(vertexStores.get(key), mempool);
		Hasher hasher = new DefaultHasher();
		SafetyRules safetyRules = new SafetyRules(key, SafetyState.initialState(), hasher);
		ScheduledTimeoutSender timeoutSender = timeoutSenders.get(key);
		FixedTimeoutPacemaker pacemaker = pacemakers.get(key);
		PendingVotes pendingVotes = new PendingVotes(hasher);
		EpochRx epochRx = () -> Observable.just(validatorSet).concatWith(Observable.never());
		EpochManager epochManager = new EpochManager(
			proposalGenerator,
			mempool,
			underlyingNetwork.getNetworkSender(key.getPublicKey()),
			safetyRules,
			pacemaker,
			vertexStores.get(key),
			pendingVotes,
			proposers -> getProposerElection(), // create a new ProposerElection per node
			key,
			counters.get(key)
		);

		return new ConsensusRunner(
			epochRx,
			underlyingNetwork.getNetworkRx(key.getPublicKey()),
			timeoutSender,
			syncSenders.get(key),
			epochManager
		);
	}

	public ProposerElection getProposerElection() {
		return new WeightedRotatingLeaders(validatorSet, Comparator.comparing(v -> v.nodeKey().euid()), 5);
	}

	public VertexStore getVertexStore(ECKeyPair keyPair) {
		return vertexStores.get(keyPair);
	}

	public SystemCounters getCounters(ECKeyPair keyPair) {
		return counters.get(keyPair);
	}

	public Completable start() {
		// Send start event once all nodes have reached real epoch event
		final CompletableSubject completableSubject = CompletableSubject.create();
		List<Completable> startedList = this.runners.values().stream()
			.map(ConsensusRunner::events)
			.map(o -> o.map(Event::getEventType)
				.filter(e -> e.equals(EventType.EPOCH))
				.firstOrError()
				.ignoreElement()
			).collect(Collectors.toList());

		Completable.merge(startedList).subscribe(completableSubject::onComplete);

		this.runners.values().forEach(ConsensusRunner::start);

		return completableSubject;
	}

	public void stop() {
		this.runners.values().forEach(ConsensusRunner::stop);
	}

	public TestEventCoordinatorNetwork getUnderlyingNetwork() {
		return underlyingNetwork;
	}

	public int getPacemakerTimeout() {
		return pacemakerTimeout;
	}
}
