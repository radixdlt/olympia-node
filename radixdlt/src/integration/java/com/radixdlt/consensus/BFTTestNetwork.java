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

import com.google.common.collect.ImmutableMap;
import com.radixdlt.identifiers.AID;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.consensus.ChainedBFT.Event;
import com.radixdlt.consensus.liveness.PacemakerImpl;
import com.radixdlt.consensus.liveness.ProposalGenerator;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * A multi-node bft test network where the network is simulated.
 */
public class BFTTestNetwork {
	private final TestEventCoordinatorNetwork testEventCoordinatorNetwork = new TestEventCoordinatorNetwork();
	private final Atom genesis;
	private final Vertex genesisVertex;
	private final QuorumCertificate genesisQC;
	private final ImmutableMap<ECKeyPair, VertexStore> vertexStores;
	private final ImmutableMap<ECKeyPair, Counters> counters;
	private final Observable<Event> bftEvents;
	private final ValidatorSet validatorSet;

	public BFTTestNetwork(List<ECKeyPair> nodes) {
		this.genesis = mock(Atom.class);
		AID aid = mock(AID.class);
		when(aid.toString()).thenReturn(Long.toString(0));
		when(this.genesis.getAID()).thenReturn(aid);
		this.genesisVertex = Vertex.createGenesis(genesis);
		this.genesisQC = new QuorumCertificate(
			new VertexMetadata(View.genesis(), genesisVertex.getId()),
			new ECDSASignatures()
		);
		this.validatorSet = ValidatorSet.from(
			nodes.stream().map(ECKeyPair::getPublicKey).map(Validator::from).collect(Collectors.toList())
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
		this.bftEvents = Observable.merge(this.vertexStores.entrySet().stream()
			.map(e -> createBFTInstance(e.getKey()).processEvents()
		).collect(Collectors.toList()));
	}

	private ChainedBFT createBFTInstance(ECKeyPair key) {
		Mempool mempool = mock(Mempool.class);
		AtomicLong atomId = new AtomicLong();
		doAnswer(inv -> {
			Atom atom = mock(Atom.class);
			AID aid = mock(AID.class);
			when(aid.toString()).thenReturn(Long.toString(atomId.incrementAndGet()));
			when(atom.getAID()).thenReturn(aid);
			return Collections.singletonList(atom);
		}).when(mempool).getAtoms(anyInt(), anySet());
		ProposalGenerator proposalGenerator = new ProposalGenerator(vertexStores.get(key), mempool);
		SafetyRules safetyRules = new SafetyRules(key, vertexStores.get(key), SafetyState.initialState());
		PacemakerImpl pacemaker = new PacemakerImpl(Executors.newSingleThreadScheduledExecutor());
		PendingVotes pendingVotes = new PendingVotes();
		EpochRx epochRx = () -> Observable.just(validatorSet).concatWith(Observable.never());
		EpochManager epochManager = new EpochManager(
			proposalGenerator,
			mempool,
			testEventCoordinatorNetwork.getNetworkSender(key.euid()),
			safetyRules,
			pacemaker,
			vertexStores.get(key),
			pendingVotes,
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

	public VertexStore getVertexStore(ECKeyPair keyPair) {
		return vertexStores.get(keyPair);
	}

	public Counters getCounters(ECKeyPair keyPair) {
		return counters.get(keyPair);
	}

	public TestEventCoordinatorNetwork getTestEventCoordinatorNetwork() {
		return testEventCoordinatorNetwork;
	}

	public Observable<Event> processBFT() {
		return this.bftEvents;
	}
}
