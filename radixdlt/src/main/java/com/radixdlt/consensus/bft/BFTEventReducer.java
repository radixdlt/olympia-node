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

package com.radixdlt.consensus.bft;

import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.liveness.Pacemaker;

import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.network.TimeSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.FormattedMessage;

import java.util.Objects;
import java.util.Optional;

/**
 * Processes and reduces BFT events to the BFT state based on core
 * BFT validation logic, any messages which must be sent to other nodes
 * are then forwarded to the BFT sender.
 */
public final class BFTEventReducer implements BFTEventProcessor {

	private static final Logger log = LogManager.getLogger();

	private final VertexStore vertexStore;
	private final Pacemaker pacemaker;
	private final EventDispatcher<ViewQuorumReached> viewQuorumReachedEventDispatcher;
	private final RemoteEventDispatcher<Vote> voteDispatcher;
	private final Hasher hasher;
	private final TimeSupplier timeSupplier;
	private final ProposerElection proposerElection;
	private final SystemCounters counters;
	private final SafetyRules safetyRules;
	private final BFTValidatorSet validatorSet;
	private final PendingVotes pendingVotes;

	private ViewUpdate latestViewUpdate = new ViewUpdate(View.genesis(), View.genesis());

	/* Indicates whether the quorum (QC or TC) has already been formed for the current view.
	 * If the quorum has been reached (but view hasn't yet been updated), subsequent votes are ignored.
	 */
	private boolean hasReachedQuorum = false;

	public BFTEventReducer(
		Pacemaker pacemaker,
		VertexStore vertexStore,
		EventDispatcher<ViewQuorumReached> viewQuorumReachedEventDispatcher,
		RemoteEventDispatcher<Vote> voteDispatcher,
		Hasher hasher,
		TimeSupplier timeSupplier,
		ProposerElection proposerElection,
		SystemCounters counters,
		SafetyRules safetyRules,
		BFTValidatorSet validatorSet,
		PendingVotes pendingVotes
	) {
		this.pacemaker = Objects.requireNonNull(pacemaker);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.viewQuorumReachedEventDispatcher = Objects.requireNonNull(viewQuorumReachedEventDispatcher);
		this.voteDispatcher = Objects.requireNonNull(voteDispatcher);
		this.hasher = Objects.requireNonNull(hasher);
		this.timeSupplier = Objects.requireNonNull(timeSupplier);
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.counters = Objects.requireNonNull(counters);
		this.safetyRules = Objects.requireNonNull(safetyRules);
		this.validatorSet = Objects.requireNonNull(validatorSet);
		this.pendingVotes = Objects.requireNonNull(pendingVotes);
	}

	@Override
	public void processBFTUpdate(BFTUpdate update) {
		log.trace("BFTUpdate: Processing {}", update);
	}

	@Override
	public void processViewUpdate(ViewUpdate viewUpdate) {
		this.hasReachedQuorum = false;
		this.latestViewUpdate = viewUpdate;
		this.pacemaker.processViewUpdate(viewUpdate);
	}

	@Override
	public void processVote(Vote vote) {
		log.trace("Vote: Processing {} from {}", vote, vote.getAuthor().getSimpleName());

		final View view = vote.getView();

		if (view.lt(this.latestViewUpdate.getCurrentView())) {
			log.trace("Vote: Ignoring vote from {} for view {}, current view is {}",
					vote.getAuthor(), view, this.latestViewUpdate.getCurrentView());
			return;
		}

		if (this.hasReachedQuorum) {
			log.trace("Vote: Ignoring vote from {} for view {}, quorum has already been reached",
					vote.getAuthor(), view);
			return;
		}

		final BFTNode nextProposer =
				this.proposerElection.getProposer(this.latestViewUpdate.getCurrentView().next());

		final VoteProcessingResult result =
				this.pendingVotes.insertVote(vote, this.validatorSet, nextProposer);

		if (result instanceof VoteProcessingResult.VoteAccepted) {
			log.trace("Vote has been processed but didn't form a quorum");
		} else if (result instanceof VoteProcessingResult.VoteRejected) {
			log.trace("Vote has been rejected because of: {}",
					((VoteProcessingResult.VoteRejected) result).getReason());
		} else if (result instanceof VoteProcessingResult.QuorumReached) {
			this.hasReachedQuorum = true;
			final ViewVotingResult viewResult =
					((VoteProcessingResult.QuorumReached) result).getViewVotingResult();
			viewQuorumReachedEventDispatcher
					.dispatch(new ViewQuorumReached(viewResult, vote.getAuthor()));
		}
	}

	@Override
	public void processProposal(Proposal proposal) {
		log.trace("Proposal: Processing {}", proposal);

		// TODO: Move into preprocessor
		final View proposedVertexView = proposal.getView();
		final View currentView = this.latestViewUpdate.getCurrentView();
		if (!currentView.equals(proposedVertexView)) {
			log.trace("Proposal: Ignoring view {}, current is: {}", proposedVertexView, currentView);
			return;
		}

		// TODO: Move insertion and maybe check into BFTSync
		final VerifiedVertex proposedVertex = new VerifiedVertex(proposal.getVertex(), this.hasher.hash(proposal.getVertex()));
		final Optional<BFTHeader> maybeHeader = this.vertexStore.insertVertex(proposedVertex);
		// The header may not be present if the ledger is ahead of consensus
		maybeHeader.ifPresent(header -> {
			final BFTNode nextLeader = this.proposerElection.getProposer(currentView.next());
			final Optional<Vote> maybeVote = this.safetyRules.voteFor(
				proposedVertex,
				header,
				this.timeSupplier.currentTime(),
				this.vertexStore.highQC()
			);
			maybeVote.ifPresentOrElse(
				vote -> {
					log.trace("Proposal: Sending vote to {}: {}", nextLeader, vote);
					this.voteDispatcher.dispatch(nextLeader, vote);
				},
				() -> {
					this.counters.increment(CounterType.BFT_REJECTED);
					log.warn(() -> new FormattedMessage("Proposal: Rejected {}", proposedVertex));
				}
			);
		});
	}

	@Override
	public void processLocalTimeout(View view) {
		log.trace("LocalTimeout: Processing {}", view);
		this.pacemaker.processLocalTimeout(view);
	}

	@Override
	public void start() {
		this.pacemaker.processQC(this.vertexStore.highQC());
	}
}
