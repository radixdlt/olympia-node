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

package com.radixdlt.consensus.bft;

import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.VoteProcessingResult.*;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.RemoteEventDispatcher;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Processes and reduces BFT events to the BFT state based on core BFT validation logic, any
 * messages which must be sent to other nodes are then forwarded to the BFT sender.
 */
// TODO: cleanup TODOs https://radixdlt.atlassian.net/browse/NT-7
public final class BFTEventReducer implements BFTEventProcessor {
  private static final Logger log = LogManager.getLogger();

  private final BFTNode self;
  private final VertexStore vertexStore;
  private final Pacemaker pacemaker;
  private final EventDispatcher<ViewQuorumReached> viewQuorumReachedEventDispatcher;
  private final EventDispatcher<NoVote> noVoteDispatcher;
  private final RemoteEventDispatcher<Vote> voteDispatcher;
  private final Hasher hasher;
  private final SafetyRules safetyRules;
  private final BFTValidatorSet validatorSet;
  private final PendingVotes pendingVotes;

  private BFTInsertUpdate latestInsertUpdate;
  private ViewUpdate latestViewUpdate;

  /* Indicates whether the quorum (QC or TC) has already been formed for the current view.
   * If the quorum has been reached (but view hasn't yet been updated), subsequent votes are ignored.
   * TODO: consider moving it to PendingVotes or elsewhere.
   */
  private boolean hasReachedQuorum = false;

  private boolean isViewTimedOut = false;

  public BFTEventReducer(
      BFTNode self,
      Pacemaker pacemaker,
      VertexStore vertexStore,
      EventDispatcher<ViewQuorumReached> viewQuorumReachedEventDispatcher,
      EventDispatcher<NoVote> noVoteDispatcher,
      RemoteEventDispatcher<Vote> voteDispatcher,
      Hasher hasher,
      SafetyRules safetyRules,
      BFTValidatorSet validatorSet,
      PendingVotes pendingVotes,
      ViewUpdate initialViewUpdate) {
    this.self = Objects.requireNonNull(self);
    this.pacemaker = Objects.requireNonNull(pacemaker);
    this.vertexStore = Objects.requireNonNull(vertexStore);
    this.viewQuorumReachedEventDispatcher =
        Objects.requireNonNull(viewQuorumReachedEventDispatcher);
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
      log.trace(
          "InsertUpdate: Ignoring insert {} for view {}, current view at {}",
          update,
          view,
          this.latestViewUpdate.getCurrentView());
      return;
    }

    this.latestInsertUpdate = update;
    this.tryVote();

    this.pacemaker.processBFTUpdate(update);
  }

  @Override
  public void processViewUpdate(ViewUpdate viewUpdate) {
    this.hasReachedQuorum = false;
    this.isViewTimedOut = false;
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

    // check if already voted in this round
    if (this.safetyRules.getLastVote(this.latestViewUpdate.getCurrentView()).isPresent()) {
      return;
    }

    // don't vote if view has timed out
    if (this.isViewTimedOut) {
      return;
    }

    // TODO: what if insertUpdate occurs before viewUpdate
    final BFTNode nextLeader = this.latestViewUpdate.getNextLeader();
    final Optional<Vote> maybeVote =
        this.safetyRules.voteFor(
            update.getInserted().getVertex(),
            update.getHeader(),
            update.getInserted().getTimeOfExecution(),
            this.latestViewUpdate.getHighQC());
    maybeVote.ifPresentOrElse(
        vote -> this.voteDispatcher.dispatch(nextLeader, vote),
        () -> this.noVoteDispatcher.dispatch(NoVote.create(update.getInserted().getVertex())));
  }

  @Override
  public void processBFTRebuildUpdate(BFTRebuildUpdate update) {
    // No-op
  }

  @Override
  public void processVote(Vote vote) {
    log.trace("Vote: Processing {}", vote);

    final View view = vote.getView();

    if (view.lt(this.latestViewUpdate.getCurrentView())) {
      log.trace(
          "Vote: Ignoring vote from {} for view {}, current view at {}",
          vote.getAuthor(),
          view,
          this.latestViewUpdate.getCurrentView());
      return;
    }

    if (this.hasReachedQuorum) {
      log.trace(
          "Vote: Ignoring vote from {} for view {}, quorum has already been reached",
          vote.getAuthor(),
          view);
      return;
    }

    if (!this.self.equals(this.latestViewUpdate.getNextLeader()) && !vote.isTimeout()) {
      log.trace("Vote: Ignoring vote from {} for view {}, unexpected vote", vote.getAuthor(), view);
      return;
    }

    switch (this.pendingVotes.insertVote(vote, this.validatorSet)) {
      case VoteAccepted ignored -> log.trace("Vote has been processed but didn't form a quorum");
      case VoteRejected voteRejected -> log.trace(
          "Vote has been rejected because of: {}", voteRejected.getReason());
      case QuorumReached quorumReached -> {
        this.hasReachedQuorum = true;
        viewQuorumReachedEventDispatcher.dispatch(
            new ViewQuorumReached(quorumReached.getViewVotingResult(), vote.getAuthor()));
      }
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
    var proposedVertex =
        new VerifiedVertex(proposal.getVertex(), this.hasher.hash(proposal.getVertex()));
    this.vertexStore.insertVertex(proposedVertex);
  }

  @Override
  public void processLocalTimeout(ScheduledLocalTimeout scheduledLocalTimeout) {
    log.trace("LocalTimeout: Processing {}", scheduledLocalTimeout);

    if (scheduledLocalTimeout.view().equals(this.latestViewUpdate.getCurrentView())) {
      this.isViewTimedOut = true;
    }

    this.pacemaker.processLocalTimeout(scheduledLocalTimeout);
  }

  @Override
  public void start() {
    this.pacemaker.start();
  }
}
