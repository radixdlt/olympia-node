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
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.liveness.Pacemaker;

import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.liveness.VoteSender;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hasher;
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
	private final BFTSyncer bftSyncer;
	private final Pacemaker pacemaker;
	private final Hasher hasher;
	private final TimeSupplier timeSupplier;
	private final ProposerElection proposerElection;
	private final VoteSender voteSender;
	private final SystemCounters counters;
	private final SafetyRules safetyRules;
	private final BFTValidatorSet validatorSet;
	private final PendingVotes pendingVotes;

	private ViewUpdate latestViewUpdate = new ViewUpdate(View.genesis(), View.genesis(), View.genesis());

	public BFTEventReducer(
		Pacemaker pacemaker,
		VertexStore vertexStore,
		BFTSyncer bftSyncer,
		Hasher hasher,
		TimeSupplier timeSupplier,
		ProposerElection proposerElection,
		VoteSender voteSender,
		SystemCounters counters,
		SafetyRules safetyRules,
		BFTValidatorSet validatorSet,
		PendingVotes pendingVotes
	) {
		this.pacemaker = Objects.requireNonNull(pacemaker);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.bftSyncer = Objects.requireNonNull(bftSyncer);
		this.hasher = Objects.requireNonNull(hasher);
		this.timeSupplier = Objects.requireNonNull(timeSupplier);
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.voteSender = Objects.requireNonNull(voteSender);
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
		this.latestViewUpdate = viewUpdate;
		this.pacemaker.processViewUpdate(viewUpdate);
	}

	@Override
	public void processVote(Vote vote) {
		log.trace("Vote: Processing {}", vote);
		// accumulate votes into QCs in store
		final View view = vote.getView();
		if (view.lte(this.latestViewUpdate.getLastQuorumView())) {
			log.trace("Vote: Ignoring vote from {} for view {}, last quorum at {}",
					vote.getAuthor(), view, this.latestViewUpdate.getLastQuorumView());
		} else {
			final Optional<QuorumCertificate> maybeQc = this.pendingVotes.insertVote(vote, this.validatorSet)
					.filter(qc -> view.gte(this.latestViewUpdate.getCurrentView()));

			maybeQc.ifPresent(qc -> {
				log.trace("Vote: Formed QC: {}", qc);
				this.counters.increment(CounterType.BFT_VOTE_QUORUMS);
				final HighQC highQC = HighQC.from(qc, this.vertexStore.highQC().highestCommittedQC());
				// If we are not yet synced, we rely on the syncer to process the QC once received
				this.bftSyncer.syncToQC(highQC, vote.getAuthor());
			});
		}
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
					this.voteSender.sendVote(vote, nextLeader);
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
