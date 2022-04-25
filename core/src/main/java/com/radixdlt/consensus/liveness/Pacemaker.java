/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.consensus.liveness;

import com.google.common.hash.HashCode;
import com.google.common.util.concurrent.RateLimiter;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
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
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.monitoring.SystemCounters.CounterType;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.utils.TimeSupplier;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Manages the pacemaker driver. */
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
  private final NextTxnsGenerator nextTxnsGenerator;
  private final Hasher hasher;
  private final RemoteEventDispatcher<Proposal> proposalDispatcher;
  private final RemoteEventDispatcher<Vote> voteDispatcher;
  private final EventDispatcher<LocalTimeoutOccurrence> timeoutDispatcher;
  private final TimeSupplier timeSupplier;
  private final SystemCounters systemCounters;

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
      NextTxnsGenerator nextTxnsGenerator,
      RemoteEventDispatcher<Proposal> proposalDispatcher,
      RemoteEventDispatcher<Vote> voteDispatcher,
      Hasher hasher,
      TimeSupplier timeSupplier,
      ViewUpdate initialViewUpdate,
      SystemCounters systemCounters) {
    this.self = Objects.requireNonNull(self);
    this.counters = Objects.requireNonNull(counters);
    this.validatorSet = Objects.requireNonNull(validatorSet);
    this.vertexStore = Objects.requireNonNull(vertexStore);
    this.safetyRules = Objects.requireNonNull(safetyRules);
    this.timeoutSender = Objects.requireNonNull(timeoutSender);
    this.timeoutDispatcher = Objects.requireNonNull(timeoutDispatcher);
    this.timeoutCalculator = Objects.requireNonNull(timeoutCalculator);
    this.nextTxnsGenerator = Objects.requireNonNull(nextTxnsGenerator);
    this.proposalDispatcher = Objects.requireNonNull(proposalDispatcher);
    this.hasher = Objects.requireNonNull(hasher);
    this.voteDispatcher = Objects.requireNonNull(voteDispatcher);
    this.timeSupplier = Objects.requireNonNull(timeSupplier);
    this.latestViewUpdate = Objects.requireNonNull(initialViewUpdate);
    this.systemCounters = Objects.requireNonNull(systemCounters);
  }

  public void start() {
    log.info("Pacemaker Start: {}", latestViewUpdate);
    this.startView();
  }

  /** Processes a local view update message * */
  public void processViewUpdate(ViewUpdate viewUpdate) {
    log.trace("View Update: {}", viewUpdate);

    final View previousView = this.latestViewUpdate.getCurrentView();
    if (viewUpdate.getCurrentView().lte(previousView)) {
      return;
    }
    this.latestViewUpdate = viewUpdate;
    this.systemCounters.set(CounterType.BFT_PACEMAKER_ROUND, viewUpdate.getCurrentView().number());

    this.startView();
  }

  /** Processes a local BFTInsertUpdate message */
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
    ScheduledLocalTimeout scheduledLocalTimeout =
        ScheduledLocalTimeout.create(latestViewUpdate, timeout);
    this.timeoutSender.dispatch(scheduledLocalTimeout, timeout);

    final BFTNode currentViewProposer = latestViewUpdate.getLeader();
    if (this.self.equals(currentViewProposer)) {
      Optional<Proposal> proposalMaybe = generateProposal(latestViewUpdate.getCurrentView());
      proposalMaybe.ifPresent(
          proposal -> {
            log.trace("Broadcasting proposal: {}", proposal);
            this.proposalDispatcher.dispatch(this.validatorSet.nodes(), proposal);
            this.counters.increment(CounterType.BFT_PACEMAKER_PROPOSALS_SENT);
          });
    }
  }

  private Optional<Proposal> generateProposal(View view) {
    final HighQC highQC = this.latestViewUpdate.getHighQC();
    final QuorumCertificate highestQC = highQC.highestQC();

    final List<Txn> nextTxns;

    // Propose null atom in the case that we are at the end of the epoch
    // TODO: Remove isEndOfEpoch knowledge from consensus
    if (highestQC.getProposed().getLedgerHeader().isEndOfEpoch()) {
      nextTxns = List.of();
    } else {
      final List<PreparedVertex> preparedVertices =
          vertexStore.getPathFromRoot(highestQC.getProposed().getVertexId());
      nextTxns = nextTxnsGenerator.generateNextTxns(view, preparedVertices);
      systemCounters.add(
          SystemCounters.CounterType.BFT_PACEMAKER_PROPOSED_TRANSACTIONS, nextTxns.size());
    }

    final UnverifiedVertex proposedVertex =
        UnverifiedVertex.create(highestQC, view, nextTxns, self);
    final VerifiedVertex verifiedVertex =
        new VerifiedVertex(proposedVertex, hasher.hash(proposedVertex));
    return safetyRules.signProposal(
        verifiedVertex, highQC.highestCommittedQC(), highQC.highestTC());
  }

  /**
   * Processes a local timeout, causing the pacemaker to either broadcast previously sent vote to
   * all nodes or broadcast a new vote for a "null" proposal. In either case, the sent vote includes
   * a timeout signature, which can later be used to form a timeout certificate.
   */
  public void processLocalTimeout(ScheduledLocalTimeout scheduledTimeout) {
    final var view = scheduledTimeout.view();

    if (!view.equals(this.latestViewUpdate.getCurrentView())) {
      log.trace(
          "LocalTimeout: Ignoring timeout {}, current is {}",
          scheduledTimeout,
          this.latestViewUpdate.getCurrentView());
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
            () -> createTimeoutVertexAndSendVote(scheduledTimeout.viewUpdate()));

    rescheduleTimeout(scheduledTimeout);
  }

  private void createTimeoutVertexAndSendVote(ViewUpdate viewUpdate) {
    if (this.timeoutVoteVertexId.isPresent()) {
      return; // vertex for a timeout vote for this view is already inserted
    }

    final var highQC = this.latestViewUpdate.getHighQC();
    final var proposedVertex =
        UnverifiedVertex.createTimeout(
            highQC.highestQC(), viewUpdate.getCurrentView(), viewUpdate.getLeader());
    final var blankVertex = new VerifiedVertex(proposedVertex, hasher.hash(proposedVertex));
    this.timeoutVoteVertexId = Optional.of(blankVertex.getId());

    // TODO: reimplement in async way
    this.vertexStore
        .getPreparedVertex(blankVertex.getId())
        .ifPresentOrElse(
            this::createAndSendTimeoutVote, // if vertex is already there, send the vote immediately
            () ->
                maybeInsertVertex(blankVertex) // otherwise insert and wait for async bft update msg
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
        new BFTHeader(
            preparedVertex.getView(), preparedVertex.getId(), preparedVertex.getLedgerHeader());

    final Vote baseVote =
        this.safetyRules.createVote(
            preparedVertex.getVertex(),
            bftHeader,
            this.timeSupplier.currentTime(),
            this.latestViewUpdate.getHighQC());

    final Vote timeoutVote = this.safetyRules.timeoutVote(baseVote);

    this.voteDispatcher.dispatch(this.validatorSet.nodes(), timeoutVote);
  }

  private void updateTimeoutCounters(ScheduledLocalTimeout scheduledTimeout) {
    if (scheduledTimeout.count() == 0) {
      counters.increment(CounterType.BFT_PACEMAKER_TIMED_OUT_ROUNDS);
    }
    counters.increment(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT);
  }

  private void rescheduleTimeout(ScheduledLocalTimeout scheduledTimeout) {
    final LocalTimeoutOccurrence localTimeoutOccurrence =
        new LocalTimeoutOccurrence(scheduledTimeout);
    this.timeoutDispatcher.dispatch(localTimeoutOccurrence);

    final long timeout = timeoutCalculator.timeout(latestViewUpdate.uncommittedViewsCount());
    final ScheduledLocalTimeout nextTimeout = scheduledTimeout.nextRetry(timeout);
    this.timeoutSender.dispatch(nextTimeout, timeout);
  }
}
