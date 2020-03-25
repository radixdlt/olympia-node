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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.common.Atom;
import com.radixdlt.consensus.liveness.PacemakerImpl;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.network.TestEventCoordinatorNetwork;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

public class MultiNodeConsensusTest {
	private final TestEventCoordinatorNetwork testEventCoordinatorNetwork = new TestEventCoordinatorNetwork();

	private Atom genesis;
	private Vertex genesisVertex;
	private QuorumCertificate genesisQC;

	@Before
	public void setup() {
		this.genesis = mock(Atom.class);
		when(this.genesis.toString()).thenReturn(Long.toHexString(0));
		this.genesisVertex = Vertex.createGenesis(genesis);
		this.genesisQC = new QuorumCertificate(
			new VertexMetadata(View.genesis(), genesisVertex.getId(), null, null),
			new ECDSASignatures()
		);
	}

	private ChainedBFT createBFTInstance(
		ECKeyPair key,
		ValidatorSet validatorSet,
		VertexStore vertexStore
	) {
		Mempool mempool = mock(Mempool.class);
		AtomicLong atomId = new AtomicLong();
		doAnswer(inv -> {
			Atom atom = mock(Atom.class);
			when(atom.toString()).thenReturn(Long.toHexString(atomId.incrementAndGet()));
			return Collections.singletonList(atom);
		}).when(mempool).getAtoms(anyInt(), anySet());
		ProposalGenerator proposalGenerator = new ProposalGenerator(vertexStore, mempool);
		SafetyRules safetyRules = new SafetyRules(key, vertexStore, SafetyState.initialState());
		PacemakerImpl pacemaker = new PacemakerImpl(Executors.newSingleThreadScheduledExecutor());
		PendingVotes pendingVotes = new PendingVotes();

		EpochRx epochRx = () -> Observable.just(validatorSet).concatWith(Observable.never());
		List<ECPublicKey> proposers = validatorSet.getValidators().stream()
			.map(Validator::nodeKey)
			.collect(Collectors.toList());
		proposers.sort(Comparator.comparing(ECPublicKey::getUID));

		EpochManager epochManager = new EpochManager(
			proposalGenerator,
			mempool,
			testEventCoordinatorNetwork.getNetworkSender(key.getUID()),
			safetyRules,
			pacemaker,
			vertexStore,
			pendingVotes,
			key
		);

		return new ChainedBFT(
			epochRx,
			testEventCoordinatorNetwork.getNetworkRx(key.getUID()),
			pacemaker,
			epochManager
		);
	}

	private List<TestObserver<Vertex>> runBFT(
		List<ECKeyPair> nodes,
		ValidatorSet validatorSet
	) {
		return nodes.stream()
			.map(e -> {
				RadixEngine radixEngine = mock(RadixEngine.class);
				when(radixEngine.staticCheck(any())).thenReturn(Optional.empty());
				VertexStore vertexStore = new VertexStore(genesisVertex, genesisQC, radixEngine);
				TestObserver<Vertex> testObserver = TestObserver.create();
				vertexStore.lastCommittedVertex().subscribe(testObserver);
				ChainedBFT chainedBFT = createBFTInstance(e, validatorSet, vertexStore);
				chainedBFT.processEvents().subscribe();
				return testObserver;
			})
			.collect(Collectors.toList());
	}

	@Test
	public void given_3_correct_bft_instances__then_all_instances_should_get_the_same_5_commits() throws Exception {
		final List<ECKeyPair> nodes = Arrays.asList(new ECKeyPair(), new ECKeyPair(), new ECKeyPair());
		final ValidatorSet validatorSet = ValidatorSet.from(
			nodes.stream().map(ECKeyPair::getPublicKey).map(Validator::from).collect(Collectors.toList())
		);
		final List<TestObserver<Vertex>> committedListeners = runBFT(nodes, validatorSet);

		final int commitCount = 5;
		for (TestObserver<Vertex> committedListener : committedListeners) {
			committedListener.awaitCount(commitCount);
		}

		for (TestObserver<Vertex> committedListener : committedListeners) {
			for (TestObserver<Vertex> otherCommittedListener : committedListeners) {
				assertThat(committedListener.values().subList(0, commitCount))
					.isEqualTo(otherCommittedListener.values().subList(0, commitCount));
			}
		}
	}

	@Test
	public void given_2_out_of_3_correct_bft_instances__then_all_instances_should_only_get_genesis_commit() throws Exception {
		final List<ECKeyPair> nodes = Arrays.asList(new ECKeyPair(), new ECKeyPair(), new ECKeyPair());
		final ValidatorSet validatorSet = ValidatorSet.from(
			nodes.stream().map(ECKeyPair::getPublicKey).map(Validator::from).collect(Collectors.toList())
		);
		testEventCoordinatorNetwork.setSendingDisable(nodes.get(2).getUID(), true);
		final List<TestObserver<Vertex>> committedListeners = runBFT(nodes, validatorSet);
		final int commitCount = 10;

		for (TestObserver<Vertex> committedListener : committedListeners) {
			committedListener.awaitCount(commitCount);
			committedListener.assertValue(v -> v.getAtom().toString().equals(Integer.toHexString(0)));
		}
	}
}
