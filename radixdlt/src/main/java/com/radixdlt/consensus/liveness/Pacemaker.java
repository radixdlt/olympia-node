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
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.MissingParentException;
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
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.network.TimeSupplier;
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
	private final BFTValidatorSet validatorSet;
	private final VertexStore vertexStore;
	private final SafetyRules safetyRules;
	private final ScheduledEventDispatcher<ScheduledLocalTimeout> timeoutSender;
	private final PacemakerTimeoutCalculator timeoutCalculator;
	private final ProposalBroadcaster proposalBroadcaster;
	private final NextCommandGenerator nextCommandGenerator;
	private final Hasher hasher;
	private final RemoteEventDispatcher<Vote> voteDispatcher;
	private final EventDispatcher<LocalTimeoutOccurrence> timeoutDispatcher;
	private final TimeSupplier timeSupplier;

	private ViewUpdate latestViewUpdate;
	private boolean isViewTimedOut = false;
	private Optional<HashCode> timeoutVoteVertexId = Optional.empty();

	public Pacemaker(
		BFTNode self,
		SystemCounters counters,
		BFTValidatorSet validatorSet,
		VertexStore vertexStore,
		SafetyRules safetyRules,
		EventDispatcher<LocalTimeoutOccurrence> timeoutDispatcher,
		ScheduledEventDispatcher<ScheduledLocalTimeout> timeoutSender,
		PacemakerTimeoutCalculator timeoutCalculator,
		NextCommandGenerator nextCommandGenerator,
		ProposalBroadcaster proposalBroadcaster,
		Hasher hasher,
		RemoteEventDispatcher<Vote> voteDispatcher,
		TimeSupplier timeSupplier,
		ViewUpdate initialViewUpdate
	) {
		this.self = Objects.requireNonNull(self);
		this.counters = Objects.requireNonNull(counters);
		this.validatorSet = Objects.requireNonNull(validatorSet);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.safetyRules = Objects.requireNonNull(safetyRules);
		this.timeoutSender = Objects.requireNonNull(timeoutSender);
		this.timeoutDispatcher = Objects.requireNonNull(timeoutDispatcher);
		this.timeoutCalculator = Objects.requireNonNull(timeoutCalculator);
		this.nextCommandGenerator = Objects.requireNonNull(nextCommandGenerator);
		this.proposalBroadcaster = Objects.requireNonNull(proposalBroadcaster);
		this.hasher = Objects.requireNonNull(hasher);
		this.voteDispatcher = Objects.requireNonNull(voteDispatcher);
		this.timeSupplier = Objects.requireNonNull(timeSupplier);
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

	/**
	 * Processes a local BFTInsertUpdate message
	 */
	public void processBFTUpdate(BFTInsertUpdate update) {
		/* we only process the insertion of an empty vertex used for timeout vote (see: processLocalTimeout) */
		if (!this.isViewTimedOut
				|| this.timeoutVoteVertexId.filter(update.getInserted().getId()::equals).isEmpty()) {
			return;
		}

		this.createAndSendTimeoutVote(update.getInserted());
	}

	private void startView() {
		this.isViewTimedOut = false;
		this.timeoutVoteVertexId = Optional.empty();

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
		final HighQC highQC = this.latestViewUpdate.getHighQC();
		final QuorumCertificate highestQC = highQC.highestQC();

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
		return safetyRules.signProposal(
			verifiedVertex,
			highQC.highestCommittedQC(),
			highQC.highestTC()
		);
	}

	/**
	 *
	 * Processes a local timeout, causing the pacemaker to
	 * either broadcast previously sent vote to all nodes
	 * or broadcast a new vote for a "null" proposal.
	 * In either case, the sent vote includes a timeout signature,
	 * which can later be used to form a timeout certificate.
	 */
	public void processLocalTimeout(ScheduledLocalTimeout scheduledTimeout) {
		final View view = scheduledTimeout.view();

		if (!view.equals(this.latestViewUpdate.getCurrentView())) {
			log.trace("LocalTimeout: Ignoring timeout {}, current is {}", scheduledTimeout, this.latestViewUpdate.getCurrentView());
			return;
		}

		log.trace("LocalTimeout: {}", scheduledTimeout);

		this.isViewTimedOut = true;

		updateTimeoutCounters(scheduledTimeout);

		this.safetyRules
			.getLastVote(view)
			.map(this.safetyRules::timeoutVote)
			.ifPresentOrElse(
				/* if there is a previously sent vote, we time it out and broadcast to all nodes */
				vote -> this.voteDispatcher.dispatch(this.validatorSet.nodes(), vote),
				/* otherwise, we asynchronously insert an empty vertex and, when done,
					we send a timeout vote on it (see processBFTUpdate) */
				() -> createTimeoutVertexAndSendVote(view));

		rescheduleTimeout(scheduledTimeout);
	}

	private void createTimeoutVertexAndSendVote(View view) {
		if (this.timeoutVoteVertexId.isPresent()) {
			return; // vertex for a timeout vote for this view is already inserted
		}

		final HighQC highQC = this.latestViewUpdate.getHighQC();
		final UnverifiedVertex proposedVertex = UnverifiedVertex.createVertex(highQC.highestQC(), view, null);
		final VerifiedVertex verifiedVertex = new VerifiedVertex(proposedVertex, hasher.hash(proposedVertex));
		this.timeoutVoteVertexId = Optional.of(verifiedVertex.getId());

		// TODO: reimplement in async way
		this.vertexStore.getPreparedVertex(verifiedVertex.getId()).ifPresentOrElse(
			this::createAndSendTimeoutVote, // if vertex is already there, send the vote immediately
			() -> maybeInsertVertex(verifiedVertex) // otherwise insert and wait for async bft update msg
		);
	}

	// FIXME: This is a temporary fix so that we can continue
	// if the vertex store is too far ahead of the pacemaker
	private void maybeInsertVertex(VerifiedVertex verifiedVertex) {
		try {
			this.vertexStore.insertVertex(verifiedVertex);
		} catch (MissingParentException e) {
			log.debug("Could not insert timeout vertex: {}", e.getMessage());
		}
	}

	private void createAndSendTimeoutVote(PreparedVertex preparedVertex) {
		final BFTHeader bftHeader =
			new BFTHeader(preparedVertex.getView(), preparedVertex.getId(), preparedVertex.getLedgerHeader());

		final Vote baseVote = this.safetyRules.createVote(
			preparedVertex.getVertex(),
			bftHeader,
			this.timeSupplier.currentTime(),
			this.latestViewUpdate.getHighQC());

		final Vote timeoutVote = this.safetyRules.timeoutVote(baseVote);

		this.voteDispatcher.dispatch(this.validatorSet.nodes(), timeoutVote);
	}

	private void  updateTimeoutCounters(ScheduledLocalTimeout scheduledTimeout) {
		if (scheduledTimeout.count() == 0) {
			counters.increment(CounterType.BFT_TIMED_OUT_VIEWS);
		}
		counters.increment(CounterType.BFT_TIMEOUT);
	}

	private void rescheduleTimeout(ScheduledLocalTimeout scheduledTimeout) {
		final LocalTimeoutOccurrence localTimeoutOccurrence = new LocalTimeoutOccurrence(scheduledTimeout);
		this.timeoutDispatcher.dispatch(localTimeoutOccurrence);

		final long timeout = timeoutCalculator.timeout(latestViewUpdate.uncommittedViewsCount());

		final Level logLevel = this.logLimiter.tryAcquire() ? Level.INFO : Level.TRACE;
		log.log(logLevel, "LocalTimeout: Restarting timeout {} for {}ms", scheduledTimeout, timeout);

		final ScheduledLocalTimeout nextTimeout = scheduledTimeout.nextRetry(timeout);
		this.timeoutSender.dispatch(nextTimeout, timeout);
	}
}
