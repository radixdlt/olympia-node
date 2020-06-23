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
import com.google.inject.Inject;
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
	private final VertexStore vertexStore;
	private final ProposerElectionFactory proposerElectionFactory;
	private final ECKeyPair selfKey;
	private final SystemCounters counters;
	private final Hasher hasher;
	private BFTEventProcessor eventProcessor = EMPTY_PROCESSOR;

	@Inject
	public EpochManager(
		Mempool mempool,
		BFTEventSender sender,
		PacemakerFactory pacemakerFactory,
		VertexStore vertexStore,
		ProposerElectionFactory proposerElectionFactory,
		Hasher hasher,
		@Named("self") ECKeyPair selfKey,
		SystemCounters counters
	) {
		this.mempool = Objects.requireNonNull(mempool);
		this.sender = Objects.requireNonNull(sender);
		this.pacemakerFactory = Objects.requireNonNull(pacemakerFactory);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.proposerElectionFactory = Objects.requireNonNull(proposerElectionFactory);
		this.selfKey = Objects.requireNonNull(selfKey);
		this.counters = Objects.requireNonNull(counters);
		this.hasher = Objects.requireNonNull(hasher);
	}

	public void processNextEpoch(Epoch epoch) {
		log.info("NEXT_EPOCH: {}", epoch);

		ValidatorSet validatorSet = epoch.getValidatorSet();
		ProposerElection proposerElection = proposerElectionFactory.create(validatorSet);
		Pacemaker pacemaker = pacemakerFactory.create();
		SafetyRules safetyRules = new SafetyRules(this.selfKey, SafetyState.initialState(), this.hasher);
		PendingVotes pendingVotes = new PendingVotes(this.hasher);
		ProposalGenerator proposalGenerator = new MempoolProposalGenerator(this.vertexStore, this.mempool);

		BFTEventReducer reducer = new BFTEventReducer(
			proposalGenerator,
			this.mempool,
			this.sender,
			safetyRules,
			pacemaker,
			this.vertexStore,
			pendingVotes,
			proposerElection,
			this.selfKey,
			validatorSet,
			counters
		);

		SyncQueues syncQueues = new SyncQueues(
			validatorSet.getValidators().stream()
				.map(Validator::nodeKey)
				.collect(ImmutableSet.toImmutableSet()),
			counters
		);

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
		vertexStore.processGetVerticesRequest(request);
	}

	public void processGetVerticesResponse(GetVerticesResponse response) {
		vertexStore.processGetVerticesResponse(response);
	}

	public void processConsensusEvent(ConsensusEvent consensusEvent) {
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
		vertexStore.processCommittedStateSync(committedStateSync);
	}
}
