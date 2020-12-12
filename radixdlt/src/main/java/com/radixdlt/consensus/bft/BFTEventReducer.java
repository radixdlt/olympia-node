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
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.liveness.Pacemaker;

import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.RemoteEventDispatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
	private final EventDispatcher<FormedQC> formedQCEventDispatcher;
	private final EventDispatcher<NoVote> noVoteDispatcher;
	private final RemoteEventDispatcher<Vote> voteDispatcher;
	private final Hasher hasher;
	private final SafetyRules safetyRules;
	private final BFTValidatorSet validatorSet;
	private final PendingVotes pendingVotes;

	private BFTInsertUpdate latestInsertUpdate;
	private ViewUpdate latestViewUpdate;

	public BFTEventReducer(
		Pacemaker pacemaker,
		VertexStore vertexStore,
		EventDispatcher<FormedQC> formedQCEventDispatcher,
		EventDispatcher<NoVote> noVoteDispatcher,
		RemoteEventDispatcher<Vote> voteDispatcher,
		Hasher hasher,
		SafetyRules safetyRules,
		BFTValidatorSet validatorSet,
		PendingVotes pendingVotes,
		ViewUpdate initialViewUpdate
	) {
		this.pacemaker = Objects.requireNonNull(pacemaker);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.formedQCEventDispatcher = Objects.requireNonNull(formedQCEventDispatcher);
		this.noVoteDispatcher = Objects.requireNonNull(noVoteDispatcher);
		this.voteDispatcher = Objects.requireNonNull(voteDispatcher);
		this.hasher = Objects.requireNonNull(hasher);
		this.safetyRules = Objects.requireNonNull(safetyRules);
		this.validatorSet = Objects.requireNonNull(validatorSet);
		this.pendingVotes = Objects.requireNonNull(pendingVotes);
		this.latestViewUpdate = Objects.requireNonNull(initialViewUpdate);
	}

	@Override
	public void processBFTUpdate(BFTInsertUpdate update) {
		log.trace("BFTUpdate: Processing {}", update);

		final View view = update.getHeader().getView();
		if (view.lt(this.latestViewUpdate.getCurrentView())) {
			log.trace("InsertUpdate: Ignoring insert {} for view {}, current view at {}",
				update, view, this.latestViewUpdate.getCurrentView());
			return;
		}

		this.latestInsertUpdate = update;
		this.tryVote();
	}

	@Override
	public void processViewUpdate(ViewUpdate viewUpdate) {
		this.latestViewUpdate = viewUpdate;
		this.pacemaker.processViewUpdate(viewUpdate);
		this.tryVote();
	}

	private void tryVote() {
		BFTInsertUpdate update = this.latestInsertUpdate;
		if (update == null) {
			return;
		}

		if (!Objects.equals(update.getHeader().getView(), this.latestViewUpdate.getCurrentView())) {
			return;
		}

		// TODO: what if insertUpdate occurs before viewUpdate
		final BFTNode nextLeader = this.latestViewUpdate.getNextLeader();
		final Optional<Vote> maybeVote = this.safetyRules.voteFor(
			update.getInserted().getVertex(),
			update.getHeader(),
			update.getInserted().getTimeOfExecution(),
			this.latestViewUpdate.getHighQC()
		);
		maybeVote.ifPresentOrElse(
			vote -> this.voteDispatcher.dispatch(nextLeader, vote),
			() -> this.noVoteDispatcher.dispatch(NoVote.create(update.getInserted().getVertex()))
		);
	}

	@Override
	public void processBFTRebuildUpdate(BFTRebuildUpdate update) {
		// No-op
	}

	@Override
	public void processVote(Vote vote) {
		log.trace("Vote: Processing {}", vote);
		// accumulate votes into QCs in store
		final View view = vote.getView();
		if (view.lt(this.latestViewUpdate.getCurrentView())) {
			log.trace("Vote: Ignoring vote from {} for view {}, current view at {}",
				vote.getAuthor(), view, this.latestViewUpdate.getCurrentView());
			return;
		}

		this.pendingVotes.insertVote(vote, this.validatorSet)
			.filter(qc -> view.gte(this.latestViewUpdate.getCurrentView()))
			.ifPresent(qc -> this.formedQCEventDispatcher.dispatch(FormedQC.create(qc, vote.getAuthor())));
	}

	@Override
	public void processViewTimeout(ViewTimeout viewTimeout) {
		log.trace("ViewTimeout: Processing {}", viewTimeout);
		this.pacemaker.processViewTimeout(viewTimeout);
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
		this.vertexStore.insertVertex(proposedVertex);
	}

	@Override
	public void processLocalTimeout(ScheduledLocalTimeout scheduledLocalTimeout) {
		log.trace("LocalTimeout: Processing {}", scheduledLocalTimeout);
		this.pacemaker.processLocalTimeout(scheduledLocalTimeout);
	}

	@Override
	public void start() {
		this.pacemaker.start();
	}
}
