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
 * Manages the pacemaker driver.
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
	private final PacemakerReducer pacemakerReducer;
	private final ScheduledEventDispatcher<ScheduledLocalTimeout> timeoutSender;
	private final PacemakerTimeoutCalculator timeoutCalculator;
	private final ProposalBroadcaster proposalBroadcaster;
	private final NextCommandGenerator nextCommandGenerator;
	private final Hasher hasher;
	private final EventDispatcher<LocalTimeoutOccurrence> timeoutDispatcher;

	private ViewUpdate latestViewUpdate;

	public Pacemaker(
		BFTNode self,
		SystemCounters counters,
		PendingViewTimeouts pendingViewTimeouts,
		BFTValidatorSet validatorSet,
		VertexStore vertexStore,
		SafetyRules safetyRules,
		VoteSender voteSender,
		EventDispatcher<LocalTimeoutOccurrence> timeoutDispatcher,
		PacemakerReducer pacemakerReducer,
		ScheduledEventDispatcher<ScheduledLocalTimeout> timeoutSender,
		PacemakerTimeoutCalculator timeoutCalculator,
		NextCommandGenerator nextCommandGenerator,
		ProposalBroadcaster proposalBroadcaster,
		Hasher hasher,
		ViewUpdate initialViewUpdate
	) {
		this.self = Objects.requireNonNull(self);
		this.counters = Objects.requireNonNull(counters);
		this.pendingViewTimeouts = Objects.requireNonNull(pendingViewTimeouts);
		this.validatorSet = Objects.requireNonNull(validatorSet);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.safetyRules = Objects.requireNonNull(safetyRules);
		this.voteSender = Objects.requireNonNull(voteSender);
		this.timeoutDispatcher = Objects.requireNonNull(timeoutDispatcher);
		this.pacemakerReducer = Objects.requireNonNull(pacemakerReducer);
		this.timeoutSender = Objects.requireNonNull(timeoutSender);
		this.timeoutCalculator = Objects.requireNonNull(timeoutCalculator);
		this.nextCommandGenerator = Objects.requireNonNull(nextCommandGenerator);
		this.proposalBroadcaster = Objects.requireNonNull(proposalBroadcaster);
		this.hasher = Objects.requireNonNull(hasher);
		this.latestViewUpdate = Objects.requireNonNull(initialViewUpdate);
	}

	public void start() {
		log.info("Pacemaker Start: {}", latestViewUpdate);
		this.startView();
	}

	/** Processes a local view update message **/
	public void processViewUpdate(ViewUpdate viewUpdate) {
		log.trace("View Update: {}", viewUpdate);

		final View previousView = this.latestViewUpdate.getCurrentView();
		if (viewUpdate.getCurrentView().lte(previousView)) {
			return;
		}
		this.latestViewUpdate = viewUpdate;

		this.startView();
	}

	private void startView() {
		long timeout = timeoutCalculator.timeout(latestViewUpdate.uncommittedViewsCount());
		ScheduledLocalTimeout scheduledLocalTimeout = ScheduledLocalTimeout.create(latestViewUpdate, timeout);
		this.timeoutSender.dispatch(scheduledLocalTimeout, timeout);

		final BFTNode currentViewProposer = latestViewUpdate.getLeader();
		if (this.self.equals(currentViewProposer)) {
			Optional<Proposal> proposalMaybe = generateProposal(latestViewUpdate.getCurrentView());
			proposalMaybe.ifPresent(proposal -> {
				log.trace("Broadcasting proposal: {}", proposal);
				this.proposalBroadcaster.broadcastProposal(proposal, this.validatorSet.nodes());
				this.counters.increment(CounterType.BFT_PROPOSALS_MADE);
			});
		}
	}

	private Optional<Proposal> generateProposal(View view) {
		// Hotstuff's Event-Driven OnBeat
		final HighQC highQC = this.latestViewUpdate.getHighQC();
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
		if (view.lt(this.latestViewUpdate.getCurrentView())) {
			log.trace("ViewTimeout: Ignoring view timeout from {} for view {}, current view at {}",
				viewTimeout.getAuthor(), view, this.latestViewUpdate.getCurrentView());
			return;
		}
		this.pendingViewTimeouts.insertViewTimeout(viewTimeout, this.validatorSet)
			.filter(v -> v.gte(this.latestViewUpdate.getCurrentView()))
			.ifPresent(vt -> {
				log.trace("ViewTimeout: Formed quorum at view {}", view);
				this.counters.increment(CounterType.BFT_TIMEOUT_QUORUMS);
				this.pacemakerReducer.updateView(view.next());
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
		final View view = scheduledTimeout.view();

		// FIXME: (Re)send timed-out vote once TCs are implemented
		log.trace("LocalTimeout: {}", scheduledTimeout);
		if (!view.equals(this.latestViewUpdate.getCurrentView())) {
			log.trace("LocalTimeout: Ignoring timeout {}, current is {}", scheduledTimeout, this.latestViewUpdate.getCurrentView());
			return;
		}

		// TODO: move counters to dispatcher side
		if (scheduledTimeout.count() == 0) {
			counters.increment(CounterType.BFT_TIMED_OUT_VIEWS);
		}
		counters.increment(CounterType.BFT_TIMEOUT);

		final ViewTimeout viewTimeout = this.safetyRules.viewTimeout(view, this.latestViewUpdate.getHighQC());
		this.voteSender.broadcastViewTimeout(viewTimeout, this.validatorSet.nodes());

		LocalTimeoutOccurrence localTimeoutOccurrence = new LocalTimeoutOccurrence(scheduledTimeout);
		this.timeoutDispatcher.dispatch(localTimeoutOccurrence);

		final long timeout = timeoutCalculator.timeout(latestViewUpdate.uncommittedViewsCount());

		Level logLevel = this.logLimiter.tryAcquire() ? Level.INFO : Level.TRACE;
		log.log(logLevel, "LocalTimeout: Restarting timeout {} for {}ms", scheduledTimeout, timeout);

		ScheduledLocalTimeout nextTimeout = scheduledTimeout.nextRetry(timeout);
		this.timeoutSender.dispatch(nextTimeout, timeout);
	}
}
