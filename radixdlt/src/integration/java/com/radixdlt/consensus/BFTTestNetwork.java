/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.consensus.ChainedBFT.Event;
import com.radixdlt.consensus.liveness.PacemakerImpl;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.liveness.RotatingLeaders;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.network.TestEventCoordinatorNetwork;
import io.reactivex.rxjava3.core.Observable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * A multi-node bft test network where the network is simulated.
 */
public class BFTTestNetwork {
	private static final int TEST_NETWORK_LATENCY = 50;
	private static final int TEST_PACEMAKER_TIMEOUT = 1000;

	private final TestEventCoordinatorNetwork testEventCoordinatorNetwork = new TestEventCoordinatorNetwork(TEST_NETWORK_LATENCY);
	private final Atom genesis;
	private final Vertex genesisVertex;
	private final QuorumCertificate genesisQC;
	private final ImmutableMap<ECKeyPair, VertexStore> vertexStores;
	private final ImmutableMap<ECKeyPair, Counters> counters;
	private final ImmutableMap<ECKeyPair, PacemakerImpl> pacemakers;
	private final Observable<Event> bftEvents;
	private final ProposerElection proposerElection;
	private final ValidatorSet validatorSet;

	public BFTTestNetwork(List<ECKeyPair> nodes) {
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
		this.vertexStores = nodes.stream()
			.collect(ImmutableMap.toImmutableMap(
				e -> e,
				e -> {
					RadixEngine radixEngine = mock(RadixEngine.class);
					when(radixEngine.staticCheck(any())).thenReturn(Optional.empty());
					return new VertexStore(genesisVertex, genesisQC, radixEngine);
				})
			);
		this.counters = nodes.stream().collect(ImmutableMap.toImmutableMap(e -> e, e -> new Counters()));
		this.pacemakers = nodes.stream().collect(ImmutableMap.toImmutableMap(e -> e,
			e -> new PacemakerImpl(TEST_PACEMAKER_TIMEOUT, Executors.newSingleThreadScheduledExecutor())));
		this.bftEvents = Observable.merge(this.vertexStores.entrySet().stream()
			.map(e -> createBFTInstance(e.getKey()).processEvents()
		).collect(Collectors.toList()));
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
			testEventCoordinatorNetwork.getNetworkSender(key.euid()),
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
			testEventCoordinatorNetwork.getNetworkRx(key.euid()),
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

	public Counters getCounters(ECKeyPair keyPair) {
		return counters.get(keyPair);
	}

	public PacemakerImpl getPacemaker(ECKeyPair keyPair) {
		return pacemakers.get(keyPair);
	}

	public TestEventCoordinatorNetwork getTestEventCoordinatorNetwork() {
		return testEventCoordinatorNetwork;
	}

	public Observable<Event> processBFT() {
		return this.bftEvents;
	}

	public int getNetworkLatency() {
		return TEST_NETWORK_LATENCY;
	}

	public int getPacemakerTimeout() {
		return TEST_PACEMAKER_TIMEOUT;
	}
}
