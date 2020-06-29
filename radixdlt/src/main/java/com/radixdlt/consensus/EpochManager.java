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

import com.google.common.collect.ImmutableSet;
import com.google.inject.name.Named;
import com.radixdlt.consensus.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.liveness.MempoolProposalGenerator;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.mempool.Mempool;
import java.util.Objects;
import javax.annotation.concurrent.NotThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages Epochs and the BFT instance associated with each epoch
 */
@NotThreadSafe
public class EpochManager {
	private static final Logger log = LogManager.getLogger("EM");
	private static final BFTEventProcessor EMPTY_PROCESSOR = new EmptyBFTEventProcessor();

	private final Mempool mempool;
	private final BFTEventSender sender;
	private final PacemakerFactory pacemakerFactory;
	private final VertexStoreFactory vertexStoreFactory;
	private final ProposerElectionFactory proposerElectionFactory;
	private final ECKeyPair selfKey;
	private final View epochChangeView;
	private final SystemCounters counters;
	private final Hasher hasher;

	private long currentEpoch;
	private VertexStore vertexStore;
	private BFTEventProcessor eventProcessor = EMPTY_PROCESSOR;

	public EpochManager(
		Mempool mempool,
		BFTEventSender sender,
		PacemakerFactory pacemakerFactory,
		VertexStoreFactory vertexStoreFactory,
		ProposerElectionFactory proposerElectionFactory,
		Hasher hasher,
		View epochChangeView,
		@Named("self") ECKeyPair selfKey,
		SystemCounters counters
	) {
		this.mempool = Objects.requireNonNull(mempool);
		this.sender = Objects.requireNonNull(sender);
		this.pacemakerFactory = Objects.requireNonNull(pacemakerFactory);
		this.vertexStoreFactory = Objects.requireNonNull(vertexStoreFactory);
		this.proposerElectionFactory = Objects.requireNonNull(proposerElectionFactory);
		this.selfKey = Objects.requireNonNull(selfKey);
		this.epochChangeView = Objects.requireNonNull(epochChangeView);
		this.counters = Objects.requireNonNull(counters);
		this.hasher = Objects.requireNonNull(hasher);
	}

	public void processEpochChange(EpochChange epochChange) {
		log.info("NEXT_EPOCH: {}", epochChange);

		ValidatorSet validatorSet = epochChange.getValidatorSet();
		ProposerElection proposerElection = proposerElectionFactory.create(validatorSet);
		Pacemaker pacemaker = pacemakerFactory.create();
		SafetyRules safetyRules = new SafetyRules(this.selfKey, SafetyState.initialState(), this.hasher);
		PendingVotes pendingVotes = new PendingVotes(this.hasher);

		VertexMetadata ancestorMetadata = epochChange.getAncestor();
		Vertex genesisVertex = Vertex.createGenesis(ancestorMetadata);
		QuorumCertificate genesisQC = QuorumCertificate.ofGenesis(genesisVertex);

		VertexStore vertexStore = vertexStoreFactory.create(genesisVertex, genesisQC);
		ProposalGenerator proposalGenerator = new MempoolProposalGenerator(vertexStore, this.mempool);

		BFTEventReducer reducer = new BFTEventReducer(
			proposalGenerator,
			this.mempool,
			this.sender,
			safetyRules,
			pacemaker,
			vertexStore,
			pendingVotes,
			proposerElection,
			this.selfKey,
			validatorSet,
			epochChangeView,
			counters
		);

		SyncQueues syncQueues = new SyncQueues(
			validatorSet.getValidators().stream()
				.map(Validator::nodeKey)
				.collect(ImmutableSet.toImmutableSet()),
			counters
		);

		this.currentEpoch = genesisVertex.getEpoch();
		this.vertexStore = vertexStore;
		this.eventProcessor = new BFTEventPreprocessor(
			this.selfKey.getPublicKey(),
			reducer,
			pacemaker,
			this.vertexStore,
			proposerElection,
			syncQueues
		);
		this.eventProcessor.start();
	}

	public void processGetVerticesRequest(GetVerticesRequest request) {
		if (this.vertexStore == null) {
			return;
		}

		vertexStore.processGetVerticesRequest(request);
	}

	public void processGetVerticesResponse(GetVerticesResponse response) {
		if (this.vertexStore == null) {
			return;
		}

		vertexStore.processGetVerticesResponse(response);
	}

	public void processConsensusEvent(ConsensusEvent consensusEvent) {
		// TODO: Add the rest of consensus event verification here

		if (consensusEvent.getEpoch() != this.currentEpoch) {
			log.warn("Received event not in the current epoch ({}): {}", this.currentEpoch, consensusEvent);
			return;
		}

		if (consensusEvent instanceof NewView) {
			eventProcessor.processNewView((NewView) consensusEvent);
		} else if (consensusEvent instanceof Proposal) {
			eventProcessor.processProposal((Proposal) consensusEvent);
		} else if (consensusEvent instanceof Vote) {
			eventProcessor.processVote((Vote) consensusEvent);
		} else {
			throw new IllegalStateException("Unknown consensus event: " + consensusEvent);
		}
	}

	public void processLocalTimeout(View view) {
		eventProcessor.processLocalTimeout(view);
	}

	public void processLocalSync(Hash synced) {
		eventProcessor.processLocalSync(synced);
	}

	public void processCommittedStateSync(CommittedStateSync committedStateSync) {
		if (vertexStore == null) {
			return;
		}

		vertexStore.processCommittedStateSync(committedStateSync);
	}
}
