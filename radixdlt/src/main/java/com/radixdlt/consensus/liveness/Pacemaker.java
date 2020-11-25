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

package com.radixdlt.consensus.liveness;

import com.google.common.hash.HashCode;
import com.google.common.util.concurrent.RateLimiter;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the pacemaker state machine.
 */
public final class Pacemaker {

	private static final Logger log = LogManager.getLogger();

	private final RateLimiter logLimiter = RateLimiter.create(1.0);

	private final BFTNode self;
	private final SystemCounters counters;
	private final PendingViewTimeouts pendingViewTimeouts;
	private final BFTValidatorSet validatorSet;
	private final VertexStore vertexStore;
	private final SafetyRules safetyRules;
	private final VoteSender voteSender;
	private final PacemakerUpdater pacemakerUpdater;
	private final ScheduledEventDispatcher<ScheduledLocalTimeout> timeoutSender;
	private final PacemakerTimeoutCalculator timeoutCalculator;
	private final ProposalBroadcaster proposalBroadcaster;
	private final ProposerElection proposerElection;
	private final NextCommandGenerator nextCommandGenerator;
	private final Hasher hasher;
	private final EventDispatcher<LocalTimeoutOccurrence> timeoutDispatcher;

	private ViewUpdate latestViewUpdate = new ViewUpdate(View.genesis(), View.genesis(), View.genesis());
	private Optional<View> lastTimedOutView = Optional.empty();

	public Pacemaker(
		BFTNode self,
		SystemCounters counters,
		PendingViewTimeouts pendingViewTimeouts,
		BFTValidatorSet validatorSet,
		VertexStore vertexStore,
		SafetyRules safetyRules,
		VoteSender voteSender,
		EventDispatcher<LocalTimeoutOccurrence> timeoutDispatcher,
		PacemakerUpdater pacemakerUpdater,
		ScheduledEventDispatcher<ScheduledLocalTimeout> timeoutSender,
		PacemakerTimeoutCalculator timeoutCalculator,
		NextCommandGenerator nextCommandGenerator,
		ProposalBroadcaster proposalBroadcaster,
		ProposerElection proposerElection,
		Hasher hasher
	) {
		this.self = Objects.requireNonNull(self);
		this.counters = Objects.requireNonNull(counters);
		this.pendingViewTimeouts = Objects.requireNonNull(pendingViewTimeouts);
		this.validatorSet = Objects.requireNonNull(validatorSet);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.safetyRules = Objects.requireNonNull(safetyRules);
		this.voteSender = Objects.requireNonNull(voteSender);
		this.timeoutDispatcher = Objects.requireNonNull(timeoutDispatcher);
		this.pacemakerUpdater = Objects.requireNonNull(pacemakerUpdater);
		this.timeoutSender = Objects.requireNonNull(timeoutSender);
		this.timeoutCalculator = Objects.requireNonNull(timeoutCalculator);
		this.nextCommandGenerator = Objects.requireNonNull(nextCommandGenerator);
		this.proposalBroadcaster = Objects.requireNonNull(proposalBroadcaster);
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.hasher = Objects.requireNonNull(hasher);
	}

	/** Processes a local view update message **/
	public void processViewUpdate(ViewUpdate viewUpdate) {
		final View previousView = this.latestViewUpdate.getCurrentView();
		this.latestViewUpdate = viewUpdate;

		final BFTNode currentViewProposer = this.proposerElection.getProposer(viewUpdate.getCurrentView());

		log.trace("View Update: {} nextLeader: {}", viewUpdate, currentViewProposer);

		if (viewUpdate.getCurrentView().gt(previousView) && this.self.equals(currentViewProposer)) {
			Optional<Proposal> proposalMaybe = generateProposal(viewUpdate.getCurrentView());
			proposalMaybe.ifPresent(proposal -> {
				log.trace("Broadcasting proposal: {}", proposal);
				this.proposalBroadcaster.broadcastProposal(proposal, this.validatorSet.nodes());
				this.counters.increment(CounterType.BFT_PROPOSALS_MADE);
			});
		}
	}

	private Optional<Proposal> generateProposal(View view) {
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
		final VerifiedVertex verifiedVertex = new VerifiedVertex(proposedVertex, hasher.hash(proposedVertex));
		return safetyRules.signProposal(verifiedVertex, highestCommitted);
	}

	/**
	 * Processes a ViewTimeout message.
	 */
	public void processViewTimeout(ViewTimeout viewTimeout) {
		final View view = viewTimeout.getView();
		if (view.lte(this.latestViewUpdate.getLastQuorumView())) {
			log.trace("ViewTimeout: Ignoring view timeout from {} for view {}, last quorum at {}",
				viewTimeout.getAuthor(), view, this.latestViewUpdate.getLastQuorumView());
			return;
		}
		this.pendingViewTimeouts.insertViewTimeout(viewTimeout, this.validatorSet)
			.filter(v -> v.gte(this.latestViewUpdate.getCurrentView()))
			.ifPresent(vt -> {
				log.trace("ViewTimeout: Formed quorum at view {}", view);
				this.counters.increment(CounterType.BFT_TIMEOUT_QUORUMS);
				this.pacemakerUpdater.updateView(view.next());
			});
	}

	/**
	 * Processes a local timeout, causing the pacemaker to broadcast a
	 * {@link com.radixdlt.consensus.ViewTimeout) to all leaders.
	 * Once a leader forms a quorum of view timeouts, it will proceed
	 * to the next view.
	 *
	 * @param view the view the local timeout is for
	 */
	// FIXME: Note functionality and Javadoc to change once TCs implemented
	public void processLocalTimeout(ScheduledLocalTimeout scheduledTimeout) {
		// FIXME: (Re)send timed-out vote once TCs are implemented
		log.trace("LocalTimeout: {}", scheduledTimeout);
		if (!scheduledTimeout.view().equals(this.latestViewUpdate.getCurrentView())) {
			log.trace("LocalTimeout: Ignoring timeout {}, current is {}", scheduledTimeout, this.latestViewUpdate.getCurrentView());
			return;
		}

		final View view = scheduledTimeout.view();

		// TODO: consider moving to a Timeout message on a dispatcher side
		if (lastTimedOutView.isEmpty() || !lastTimedOutView.get().equals(view)) {
			counters.increment(CounterType.BFT_TIMED_OUT_VIEWS);
		}
		lastTimedOutView = Optional.of(view);
		counters.increment(CounterType.BFT_TOTAL_VIEW_TIMEOUTS);

		final ViewTimeout viewTimeout = this.safetyRules.viewTimeout(view, this.vertexStore.highQC());
		this.voteSender.broadcastViewTimeout(viewTimeout, this.validatorSet.nodes());

		BFTNode leader = proposerElection.getProposer(view);
		LocalTimeoutOccurrence localTimeoutOccurrence = new LocalTimeoutOccurrence(view, leader);
		this.timeoutDispatcher.dispatch(localTimeoutOccurrence);

		final long timeout = timeoutCalculator.timeout(latestViewUpdate.uncommittedViewsCount());

		Level logLevel = this.logLimiter.tryAcquire() ? Level.INFO : Level.TRACE;
		log.log(logLevel, "LocalTimeout: Restarting timeout {} for {}ms", scheduledTimeout, timeout);

		ScheduledLocalTimeout nextTimeout = new ScheduledLocalTimeout(scheduledTimeout.viewUpdate(), timeout);
		this.timeoutSender.dispatch(nextTimeout, timeout);
	}

	/**
	 * Signifies to the pacemaker that a quorum has agreed that a view has
	 * been completed.
	 *
	 * @param highQC the sync info for the view
	 * @return {@code true} if proceeded to a new view
	 */
	public boolean processQC(HighQC highQC) {
		return this.pacemakerUpdater.processQC(highQC);
	}
}
