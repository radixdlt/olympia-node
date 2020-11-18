/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.liveness;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.HashCode;
import com.google.common.util.concurrent.RateLimiter;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.network.TimeSupplier;

import java.util.Set;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.FormattedMessage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A pacemaker which utilizes a exponential timeout.
 */
public final class ExponentialTimeoutPacemaker implements Pacemaker {

	/**
	 * Sender of information regarding the BFT
	 */
	public interface PacemakerInfoSender {

		/**
		 * Signify that the bft node is starting a new view
		 * @param view the view the bft node has changed to
		 */
		void sendCurrentView(View view);

		/**
		 * Signify that a timeout was processed by this bft node
		 * @param view the view of the timeout
		 */
		void sendTimeoutProcessed(View view);
	}

	private static final Logger log = LogManager.getLogger();

	private final long timeoutMilliseconds;
	private final double rate;
	private final int maxExponent;

	private final BFTNode self;
	private final SystemCounters counters;

	private final PendingVotes pendingVotes;
	private final PendingViewTimeouts pendingViewTimeouts;
	private final BFTValidatorSet validatorSet;

	private final VertexStore vertexStore;
	private final ProposerElection proposerElection;

	private final SafetyRules safetyRules;
	private final NextCommandGenerator nextCommandGenerator;
	private final TimeSupplier timeSupplier;
	private final Hasher hasher;

	private final ProposalBroadcaster sender;
	private final ProceedToViewSender proceedToViewSender;
	private final PacemakerTimeoutSender timeoutSender;
	private final RemoteEventDispatcher<Vote> voteDispatcher;
	private final PacemakerInfoSender pacemakerInfoSender;

	private final RateLimiter logLimiter = RateLimiter.create(1.0);

	private View currentView = View.genesis();
	// Highest view in which a commit happened
	private View highestCommitView = View.genesis();
	// Last view that we had any kind of quorum for
	private View lastQuorumView = View.genesis();

	public ExponentialTimeoutPacemaker(
		long timeoutMilliseconds,
		double rate,
		int maxExponent,

		BFTNode self,
		SystemCounters counters,

		PendingVotes pendingVotes,
		PendingViewTimeouts pendingViewTimeouts,
		BFTValidatorSet validatorSet,

		VertexStore vertexStore,
		ProposerElection proposerElection,

		SafetyRules safetyRules,
		NextCommandGenerator nextCommandGenerator,
		TimeSupplier timeSupplier,
		Hasher hasher,

		ProposalBroadcaster sender,
		ProceedToViewSender proceedToViewSender,
		RemoteEventDispatcher<Vote> voteDispatcher,
		PacemakerTimeoutSender timeoutSender,
		PacemakerInfoSender pacemakerInfoSender
	) {
		if (timeoutMilliseconds <= 0) {
			throw new IllegalArgumentException("timeoutMilliseconds must be > 0 but was " + timeoutMilliseconds);
		}
		if (rate <= 1.0) {
			throw new IllegalArgumentException("rate must be > 1.0, but was " + rate);
		}
		if (maxExponent < 0) {
			throw new IllegalArgumentException("maxExponent must be >= 0, but was " + maxExponent);
		}
		double maxTimeout = timeoutMilliseconds * Math.pow(rate, maxExponent);
		if (maxTimeout > Long.MAX_VALUE) {
			throw new IllegalArgumentException("Maximum timeout value of " + maxTimeout + " is too large");
		}
		this.timeoutMilliseconds = timeoutMilliseconds;
		this.rate = rate;
		this.maxExponent = maxExponent;

		this.self = Objects.requireNonNull(self);
		this.counters = Objects.requireNonNull(counters);

		this.pendingVotes = Objects.requireNonNull(pendingVotes);
		this.pendingViewTimeouts = Objects.requireNonNull(pendingViewTimeouts);
		this.validatorSet = Objects.requireNonNull(validatorSet);

		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.safetyRules = Objects.requireNonNull(safetyRules);
		this.nextCommandGenerator = Objects.requireNonNull(nextCommandGenerator);
		this.timeSupplier = Objects.requireNonNull(timeSupplier);
		this.hasher = Objects.requireNonNull(hasher);

		this.sender = Objects.requireNonNull(sender);
		this.proceedToViewSender = Objects.requireNonNull(proceedToViewSender);
		this.voteDispatcher = Objects.requireNonNull(voteDispatcher);
		this.timeoutSender = Objects.requireNonNull(timeoutSender);
		this.pacemakerInfoSender = Objects.requireNonNull(pacemakerInfoSender);
		log.debug("{} for {} with max timeout {}*{}^{}ms",
			getClass().getSimpleName(), this.self, this.timeoutMilliseconds, this.rate, this.maxExponent);

	}

	@Override
	public View getCurrentView() {
		return this.currentView;
	}

	// FIXME: To be moved out of pacemaker
	@Override
	public Optional<QuorumCertificate> processVote(Vote vote) {
		View view = vote.getView();
		if (view.compareTo(this.lastQuorumView) <= 0) {
			log.debug("Vote: Ignoring vote from {} for view {}, last quorum at {}", vote.getAuthor(), view, this.lastQuorumView);
			return Optional.empty();
		}
		Optional<QuorumCertificate> maybeQC = this.pendingVotes.insertVote(vote, this.validatorSet)
			.filter(qc -> shouldProceedToNextView(view));
		maybeQC.ifPresent(qc -> this.lastQuorumView = view);
		return maybeQC;
	}

	// FIXME: To be moved out of pacemaker
	@Override
	public void processProposal(Proposal proposal) {
		log.trace("Proposal: Processing {}", proposal);
		final View proposedVertexView = proposal.getView();
		if (!this.currentView.equals(proposedVertexView)) {
			log.trace("Proposal: Ignoring view {}, current is: {}", proposedVertexView, this.currentView);
			return;
		}

		final VerifiedVertex proposedVertex = new VerifiedVertex(proposal.getVertex(), this.hasher.hash(proposal.getVertex()));
		final Optional<BFTHeader> maybeHeader = this.vertexStore.insertVertex(proposedVertex);
		// The header may not be present if the ledger is ahead of consensus
		maybeHeader.ifPresent(header -> {
			final Optional<Vote> maybeVote = this.safetyRules.voteFor(
				proposedVertex,
				header,
				this.timeSupplier.currentTime(),
				this.vertexStore.highQC()
			);
			maybeVote.ifPresentOrElse(
				vote -> {
					final BFTNode nextLeader = this.proposerElection.getProposer(this.currentView.next());
					log.trace("Proposal: Sending vote to {}: {}", nextLeader, vote);
					this.voteDispatcher.dispatch(nextLeader, vote);
				},
				() -> {
					this.counters.increment(CounterType.BFT_REJECTED);
					log.warn(() -> new FormattedMessage("Proposal: Rejected {}", proposedVertex));
				});
		});
	}


	@Override
	public void processViewTimeout(ViewTimeout viewTimeout) {
		View view = viewTimeout.getView();
		if (view.compareTo(this.lastQuorumView) <= 0) {
			log.debug("ViewTimeout: Ignoring view timeout from {} for view {}, last quorum at {}",
				viewTimeout.getAuthor(), view, this.lastQuorumView);
			return;
		}
		this.pendingViewTimeouts.insertViewTimeout(viewTimeout, this.validatorSet)
			.filter(this::shouldProceedToNextView)
			.ifPresent(vt -> {
				log.trace("ViewTimeout: Formed quorum at view {}", view);
				this.counters.increment(CounterType.BFT_TIMEOUT_QUORUMS);
				this.lastQuorumView = view;
				this.updateView(view.next());
			});
	}

	@Override
	public void processLocalTimeout(View view) {
		// FIXME: (Re)send timed-out vote once TCs are implemented
		log.trace("LocalTimeout: view {}", view);
		if (!view.equals(this.currentView)) {
			log.trace("LocalTimeout: Ignoring view {}, current is {}", view, this.currentView);
			return;
		}
		counters.increment(CounterType.BFT_TIMEOUT);

		ViewTimeout viewTimeout = this.safetyRules.viewTimeout(view, this.vertexStore.highQC());
		this.proceedToViewSender.broadcastViewTimeout(viewTimeout, this.validatorSet.nodes());
		this.pacemakerInfoSender.sendTimeoutProcessed(view);

		Level logLevel = this.logLimiter.tryAcquire() ? Level.INFO : Level.TRACE;
		long timeout = timeout(uncommittedViews(view));
		log.log(logLevel, "LocalTimeout: Restarting view {} timeout for {}ms", view, timeout);
		this.timeoutSender.scheduleTimeout(view, timeout);
	}

	@Override
	public boolean processQC(HighQC highQC) {
		log.trace("QuorumCertificate: {}", highQC);
		// check if a new view can be started
		View view = highQC.highestQC().getView();
		if (shouldProceedToNextView(view)) {
			this.lastQuorumView = view;
			this.highestCommitView = highQC.highestCommittedQC().getView();
			this.updateView(view.next());
			return true;
		}
		log.trace("Ignoring QC for view {}: current view is {}", view, this.currentView);
		return false;
	}

	@VisibleForTesting
	View highestCommitView() {
		return this.highestCommitView;
	}

	private boolean shouldProceedToNextView(View view) {
		return view.next().compareTo(this.currentView) > 0;
	}

	private void updateView(View nextView) {
		Level logLevel = this.logLimiter.tryAcquire() ? Level.INFO : Level.TRACE;
		long timeout = timeout(uncommittedViews(nextView));
		log.log(logLevel, "Starting View: {} with timeout {}ms", nextView, timeout);
		this.currentView = nextView;
		this.timeoutSender.scheduleTimeout(this.currentView, timeout);
		this.pacemakerInfoSender.sendCurrentView(this.currentView);
		if (this.self.equals(this.proposerElection.getProposer(nextView))) {
			Proposal proposal = generateProposal(this.currentView);
			log.trace("Broadcasting proposal: {}", proposal);
			this.sender.broadcastProposal(proposal, this.validatorSet.nodes());
			this.counters.increment(CounterType.BFT_PROPOSALS_MADE);
		}
	}

	private Proposal generateProposal(View view) {
		// Hotstuff's Event-Driven OnBeat
		final HighQC highQC = this.vertexStore.highQC();
		final QuorumCertificate highestQC = highQC.highestQC();
		final QuorumCertificate highestCommitted = highQC.highestCommittedQC();

		final Command nextCommand;

		// Propose null atom in the case that we are at the end of the epoch
		// TODO: Remove isEndOfEpoch knowledge from consensus
		if (highestQC.getProposed().getLedgerHeader().isEndOfEpoch()) {
			nextCommand = null;
		} else {
			final List<PreparedVertex> preparedVertices = vertexStore.getPathFromRoot(highestQC.getProposed().getVertexId());
			final Set<HashCode> prepared = preparedVertices.stream()
				.flatMap(PreparedVertex::getCommands)
				.filter(Objects::nonNull)
				.map(hasher::hash)
				.collect(Collectors.toSet());

			nextCommand = nextCommandGenerator.generateNextCommand(view, prepared);
		}

		final UnverifiedVertex proposedVertex = UnverifiedVertex.createVertex(highestQC, view, nextCommand);
		return safetyRules.signProposal(proposedVertex, highestCommitted);
	}

	private long uncommittedViews(View v) {
		return Math.max(0L, v.number() - this.highestCommitView.number() - 1);
	}

	private long timeout(long uncommittedViews) {
		double exponential = Math.pow(this.rate, Math.min(this.maxExponent, uncommittedViews));
		return Math.round(this.timeoutMilliseconds * exponential);
	}
}
