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

package com.radixdlt.consensus.functional;

import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.BFTEventPreprocessor;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.DefaultHasher;
import com.radixdlt.consensus.BFTEventSender;
import com.radixdlt.consensus.GetVertexRequest;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.BFTEventReducer;
import com.radixdlt.consensus.SyncQueues;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.VertexStore;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker.TimeoutSender;
import com.radixdlt.consensus.liveness.MempoolProposalGenerator;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.consensus.sync.SyncedRadixEngine;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.mempool.EmptyMempool;
import com.radixdlt.mempool.Mempool;
import java.util.stream.Collectors;

/**
 * Controlled BFT Node where its state machine is managed by a synchronous
 * processNext() call.
 */
class ControlledBFTNode {
	private final BFTEventProcessor ec;
	private final SystemCounters systemCounters;
	private final VertexStore vertexStore;

	ControlledBFTNode(
		ECKeyPair key,
		BFTEventSender sender,
		ProposerElection proposerElection,
		ValidatorSet validatorSet
	) {
		this.systemCounters = new SystemCountersImpl();
		Vertex genesisVertex = Vertex.createGenesis(null);
		QuorumCertificate genesisQC = new QuorumCertificate(
			new VoteData(VertexMetadata.ofVertex(genesisVertex), null, null),
			new ECDSASignatures()
		);
		SyncedRadixEngine re = mock(SyncedRadixEngine.class);
		this.vertexStore = new VertexStore(genesisVertex, genesisQC, re, systemCounters);
		Mempool mempool = new EmptyMempool();
		ProposalGenerator proposalGenerator = new MempoolProposalGenerator(vertexStore, mempool);
		TimeoutSender timeoutSender = mock(TimeoutSender.class);
		// Timeout doesn't matter here
		Pacemaker pacemaker = new FixedTimeoutPacemaker(1, timeoutSender);
		Hasher hasher = new DefaultHasher();
		SafetyRules safetyRules = new SafetyRules(key, SafetyState.initialState(), hasher);
		PendingVotes pendingVotes = new PendingVotes(hasher);
		BFTEventReducer reducer = new BFTEventReducer(
			proposalGenerator,
			mempool,
			sender,
			safetyRules,
			pacemaker,
			vertexStore,
			pendingVotes,
			proposerElection,
			key,
			validatorSet,
			systemCounters
		);
		SyncQueues syncQueues = new SyncQueues(
			validatorSet.getValidators().stream().map(Validator::nodeKey).collect(Collectors.toSet()),
			systemCounters
		);

		this.ec = new BFTEventPreprocessor(
			key.getPublicKey(),
			reducer,
			pacemaker,
			vertexStore,
			proposerElection,
			syncQueues,
			systemCounters
		);
	}

	SystemCounters getSystemCounters() {
		return systemCounters;
	}

	void start() {
		ec.start();
	}

	void processNext(Object msg) {
		if (msg instanceof GetVertexRequest) {
			ec.processGetVertexRequest((GetVertexRequest) msg);
		} else if (msg instanceof View) {
			ec.processLocalTimeout((View) msg);
		} else if (msg instanceof NewView) {
			ec.processNewView((NewView) msg);
		} else if (msg instanceof Proposal) {
			ec.processProposal((Proposal) msg);
		} else if (msg instanceof Vote) {
			ec.processVote((Vote) msg);
		} else {
			throw new IllegalStateException("Unknown msg: " + msg);
		}
	}
}
