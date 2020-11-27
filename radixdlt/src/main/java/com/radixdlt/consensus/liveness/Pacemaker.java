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
import com.radixdlt.environment.RemoteEventDispatcher;
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
 * Manages the pacemaker state machine.
 */
public final class Pacemaker {

	private static final Logger log = LogManager.getLogger();

	private final RateLimiter logLimiter = RateLimiter.create(1.0);

	private final BFTNode self;
	private final SystemCounters counters;
	private final BFTValidatorSet validatorSet;
	private final VertexStore vertexStore;
	private final SafetyRules safetyRules;
	private final PacemakerInfoSender pacemakerInfoSender;
	private final PacemakerState pacemakerState;
	private final PacemakerTimeoutSender timeoutSender;
	private final PacemakerTimeoutCalculator timeoutCalculator;
	private final ProposalBroadcaster proposalBroadcaster;
	private final ProposerElection proposerElection;
	private final NextCommandGenerator nextCommandGenerator;
	private final Hasher hasher;
	private final RemoteEventDispatcher<Vote> voteDispatcher;
	private final TimeSupplier timeSupplier;

	private ViewUpdate latestViewUpdate = new ViewUpdate(View.genesis(), View.genesis());
	private Optional<View> lastTimedOutView = Optional.empty();

	public Pacemaker(
			BFTNode self,
			SystemCounters counters,
			BFTValidatorSet validatorSet,
			VertexStore vertexStore,
			SafetyRules safetyRules,
			PacemakerInfoSender pacemakerInfoSender,
			PacemakerState pacemakerState,
			PacemakerTimeoutSender timeoutSender,
			PacemakerTimeoutCalculator timeoutCalculator,
			NextCommandGenerator nextCommandGenerator,
			ProposalBroadcaster proposalBroadcaster,
			ProposerElection proposerElection,
			Hasher hasher,
			RemoteEventDispatcher<Vote> voteDispatcher,
			TimeSupplier timeSupplier
	) {
		this.self = Objects.requireNonNull(self);
		this.counters = Objects.requireNonNull(counters);
		this.validatorSet = Objects.requireNonNull(validatorSet);
		this.vertexStore = Objects.requireNonNull(vertexStore);
		this.safetyRules = Objects.requireNonNull(safetyRules);
		this.pacemakerInfoSender = Objects.requireNonNull(pacemakerInfoSender);
		this.pacemakerState = Objects.requireNonNull(pacemakerState);
		this.timeoutSender = Objects.requireNonNull(timeoutSender);
		this.timeoutCalculator = Objects.requireNonNull(timeoutCalculator);
		this.nextCommandGenerator = Objects.requireNonNull(nextCommandGenerator);
		this.proposalBroadcaster = Objects.requireNonNull(proposalBroadcaster);
		this.proposerElection = Objects.requireNonNull(proposerElection);
		this.hasher = Objects.requireNonNull(hasher);
		this.voteDispatcher = Objects.requireNonNull(voteDispatcher);
		this.timeSupplier = Objects.requireNonNull(timeSupplier);
	}

	/** Processes a local view update message **/
	public void processViewUpdate(ViewUpdate viewUpdate) {
		final View previousView = this.latestViewUpdate.getCurrentView();
		this.latestViewUpdate = viewUpdate;

		final BFTNode currentViewProposer = this.proposerElection.getProposer(viewUpdate.getCurrentView());

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
		final HighQC highQC = this.vertexStore.highQC();
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
	 *
	 * @param view the view the local timeout is for
	 */
	public void processLocalTimeout(View view) {
		log.trace("LocalTimeout: view {}", view);
		if (!view.equals(this.latestViewUpdate.getCurrentView())) {
			log.trace("LocalTimeout: Ignoring view {}, current is {}", view, this.latestViewUpdate.getCurrentView());
			return;
		}

		updateTimeoutCounters(view);

		final Optional<Vote> maybeLastVote = this.safetyRules.getLastVote(view);
		final Optional<Vote> maybeTimeoutVote = maybeLastVote
				.or(() -> createEmptyVote(view))
				.map(this.safetyRules::timeoutVote);

		maybeTimeoutVote.ifPresentOrElse(
			timeoutVote -> this.voteDispatcher.dispatch(this.validatorSet.nodes(), timeoutVote),
			() -> log.warn("No timeout vote to send")
		);

		this.pacemakerInfoSender.sendTimeoutProcessed(view);
		rescheduleTimeout(view);
	}

	private Optional<Vote> createEmptyVote(View view) {
		final HighQC highQC = this.vertexStore.highQC();
		final UnverifiedVertex proposedVertex = UnverifiedVertex.createVertex(highQC.highestQC(), view, null);
		final VerifiedVertex verifiedVertex = new VerifiedVertex(proposedVertex, hasher.hash(proposedVertex));
		final long currentTime = this.timeSupplier.currentTime();
		return this.vertexStore.insertVertex(verifiedVertex)
			.map(header -> this.safetyRules.createVote(verifiedVertex, header, currentTime, highQC));
	}

	private void  updateTimeoutCounters(View view) {
		if (lastTimedOutView.isEmpty() || !lastTimedOutView.get().equals(view)) {
			counters.increment(CounterType.BFT_TIMED_OUT_VIEWS);
		}
		lastTimedOutView = Optional.of(view);
		counters.increment(CounterType.BFT_TOTAL_VIEW_TIMEOUTS);
	}

	private void rescheduleTimeout(View view) {
		final long timeout = timeoutCalculator.timeout(latestViewUpdate.uncommittedViewsCount());

		Level logLevel = this.logLimiter.tryAcquire() ? Level.INFO : Level.TRACE;
		log.log(logLevel, "LocalTimeout: Restarting view {} timeout for {}ms", view, timeout);

		this.timeoutSender.scheduleTimeout(latestViewUpdate.getCurrentView(), timeout);
	}

	/**
	 * Signifies to the pacemaker that a quorum has agreed that a view has
	 * been completed.
	 *
	 * @param highQC the sync info for the view
	 */
	public void processQC(HighQC highQC) {
		this.pacemakerState.processQC(highQC);
	}
}
