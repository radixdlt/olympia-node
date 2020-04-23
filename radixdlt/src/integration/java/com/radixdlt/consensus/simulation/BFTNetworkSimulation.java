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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.consensus.ChainedBFT;
import com.radixdlt.consensus.ChainedBFT.Event;
import com.radixdlt.consensus.DefaultHasher;
import com.radixdlt.consensus.EpochManager;
import com.radixdlt.consensus.EpochRx;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.VertexStore;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.liveness.PacemakerImpl;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.liveness.RotatingLeaders;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.mempool.Mempool;

import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork;
import io.reactivex.rxjava3.core.Observable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * A multi-node bft test network where the network and latencies of each message is simulated.
 */
public class BFTNetworkSimulation {
	private static final int TEST_PACEMAKER_TIMEOUT = 1000;

	private final int pacemakerTimeout;
	private final TestEventCoordinatorNetwork underlyingNetwork;
	private final Atom genesis;
	private final Vertex genesisVertex;
	private final QuorumCertificate genesisQC;
	private final ImmutableMap<ECKeyPair, VertexStore> vertexStores;
	private final ImmutableMap<ECKeyPair, SystemCounters> counters;
	private final ImmutableMap<ECKeyPair, PacemakerImpl> pacemakers;
	private final Observable<Event> bftEvents;
	private final ProposerElection proposerElection;
	private final ValidatorSet validatorSet;
	private final List<ECKeyPair> nodes;

	/**
	 * Create a BFT test network with an underlying simulated network
	 * @param nodes The nodes to populate the network with
	 * @param underlyingNetwork the network simulator
	 */
	public BFTNetworkSimulation(List<ECKeyPair> nodes, TestEventCoordinatorNetwork underlyingNetwork) {
		this(nodes, underlyingNetwork, TEST_PACEMAKER_TIMEOUT);
	}

	/**
	 * Create a BFT test network with an underlying simulated network.
	 * @param nodes The nodes to populate the network with
	 * @param underlyingNetwork the network simulator
	 * @param pacemakerTimeout a fixed pacemaker timeout used for all nodes
	 */
	public BFTNetworkSimulation(List<ECKeyPair> nodes, TestEventCoordinatorNetwork underlyingNetwork, int pacemakerTimeout) {
		this.nodes = nodes;
		this.underlyingNetwork = Objects.requireNonNull(underlyingNetwork);
		this.pacemakerTimeout = pacemakerTimeout;
		this.genesis = null;
		this.genesisVertex = Vertex.createGenesis(genesis);
		this.genesisQC = new QuorumCertificate(
			new VoteData(VertexMetadata.ofVertex(genesisVertex), null, null),
			new ECDSASignatures()
		);
		this.validatorSet = ValidatorSet.from(
			nodes.stream().map(ECKeyPair::getPublicKey).map(Validator::from).collect(Collectors.toList())
		);
		this.proposerElection = new RotatingLeaders(validatorSet.getValidators().stream()
			.map(Validator::nodeKey)
			.collect(ImmutableList.toImmutableList())
		);
		this.counters = nodes.stream().collect(ImmutableMap.toImmutableMap(e -> e, e -> new SystemCountersImpl()));
		this.vertexStores = nodes.stream()
			.collect(ImmutableMap.toImmutableMap(
				e -> e,
				e -> {
					RadixEngine radixEngine = mock(RadixEngine.class);
					when(radixEngine.staticCheck(any())).thenReturn(Optional.empty());
					return new VertexStore(genesisVertex, genesisQC, radixEngine, this.counters.get(e));
				})
			);
		this.pacemakers = nodes.stream().collect(ImmutableMap.toImmutableMap(e -> e,
			e -> new PacemakerImpl(this.pacemakerTimeout, Executors.newSingleThreadScheduledExecutor())));
		this.bftEvents = Observable.merge(this.vertexStores.keySet().stream()
			.map(vertexStore -> createBFTInstance(vertexStore).processEvents())
			.collect(Collectors.toList()));
	}

	public List<ECKeyPair> getNodes() {
		return nodes;
	}

	private ChainedBFT createBFTInstance(ECKeyPair key) {
		Mempool mempool = mock(Mempool.class);
		doAnswer(inv -> Collections.emptyList()).when(mempool).getAtoms(anyInt(), anySet());
		ProposalGenerator proposalGenerator = new ProposalGenerator(vertexStores.get(key), mempool);
		Hasher hasher = new DefaultHasher();
		SafetyRules safetyRules = new SafetyRules(key, SafetyState.initialState(), hasher);
		PacemakerImpl pacemaker = pacemakers.get(key);
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
			proposers -> proposerElection, // assumes all instances use the same validators
			key,
			counters.get(key)
		);

		return new ChainedBFT(
			epochRx,
			underlyingNetwork.getNetworkRx(key.getPublicKey()),
			pacemaker,
			epochManager
		);
	}

	public ProposerElection getProposerElection() {
		return proposerElection;
	}

	public VertexStore getVertexStore(ECKeyPair keyPair) {
		return vertexStores.get(keyPair);
	}

	public SystemCounters getCounters(ECKeyPair keyPair) {
		return counters.get(keyPair);
	}

	public PacemakerImpl getPacemaker(ECKeyPair keyPair) {
		return pacemakers.get(keyPair);
	}

	public TestEventCoordinatorNetwork getUnderlyingNetwork() {
		return underlyingNetwork;
	}

	public Observable<Event> processBFT() {
		return this.bftEvents;
	}

	public int getPacemakerTimeout() {
		return pacemakerTimeout;
	}
}
