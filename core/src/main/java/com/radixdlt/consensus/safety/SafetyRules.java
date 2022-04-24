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

package com.radixdlt.consensus.safety;

import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimeoutCertificate;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.liveness.VoteTimeout;
import com.radixdlt.consensus.safety.SafetyState.Builder;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.Hasher;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Manages safety of the protocol. */
public final class SafetyRules {
  private static final Logger logger = LogManager.getLogger();

  private final BFTNode self;
  private final Hasher hasher;
  private final HashSigner signer;
  private final PersistentSafetyStateStore persistentSafetyStateStore;

  private SafetyState state;

  @Inject
  public SafetyRules(
      @Self BFTNode self,
      SafetyState initialState,
      PersistentSafetyStateStore persistentSafetyStateStore,
      Hasher hasher,
      HashSigner signer) {
    this.self = self;
    this.state = Objects.requireNonNull(initialState);
    this.persistentSafetyStateStore = Objects.requireNonNull(persistentSafetyStateStore);
    this.hasher = Objects.requireNonNull(hasher);
    this.signer = Objects.requireNonNull(signer);
  }

  private boolean checkLastVoted(VerifiedVertex proposedVertex) {
    // ensure vertex does not violate earlier votes
    if (proposedVertex.getView().lte(this.state.getLastVotedView())) {
      logger.warn(
          "Safety warning: Vertex {} violates earlier vote at view {}",
          proposedVertex,
          this.state.getLastVotedView());
      return false;
    } else {
      return true;
    }
  }

  private boolean checkLocked(VerifiedVertex proposedVertex, Builder nextStateBuilder) {
    if (proposedVertex.getParentHeader().getView().lt(this.state.getLockedView())) {
      logger.warn(
          "Safety warning: Vertex {} does not respect locked view {}",
          proposedVertex,
          this.state.getLockedView());
      return false;
    }

    // pre-commit phase on consecutive qc's proposed vertex
    if (proposedVertex.getGrandParentHeader().getView().compareTo(this.state.getLockedView()) > 0) {
      nextStateBuilder.lockedView(proposedVertex.getGrandParentHeader().getView());
    }
    return true;
  }

  /**
   * Create a signed proposal from a vertex
   *
   * @param proposedVertex vertex to sign
   * @param highestCommittedQC highest known committed QC
   * @param highestTC highest known TC
   * @return signed proposal object for consensus
   */
  public Optional<Proposal> signProposal(
      VerifiedVertex proposedVertex,
      QuorumCertificate highestCommittedQC,
      Optional<TimeoutCertificate> highestTC) {
    final Builder safetyStateBuilder = this.state.toBuilder();
    if (!checkLocked(proposedVertex, safetyStateBuilder)) {
      return Optional.empty();
    }

    this.state = safetyStateBuilder.build();

    final ECDSASignature signature = this.signer.sign(proposedVertex.getId());
    return Optional.of(
        new Proposal(proposedVertex.toSerializable(), highestCommittedQC, signature, highestTC));
  }

  private static VoteData constructVoteData(
      VerifiedVertex proposedVertex, BFTHeader proposedHeader) {
    final BFTHeader parent = proposedVertex.getParentHeader();

    // Add a vertex to commit if creating a quorum for the proposed vertex would
    // create three consecutive qcs.
    final BFTHeader toCommit;
    if (proposedVertex.touchesGenesis()
        || !proposedVertex.hasDirectParent()
        || !proposedVertex.parentHasDirectParent()) {
      toCommit = null;
    } else {
      toCommit = proposedVertex.getGrandParentHeader();
    }

    return new VoteData(proposedHeader, parent, toCommit);
  }

  /**
   * Vote for a proposed vertex while ensuring that safety invariants are upheld.
   *
   * @param proposedVertex The proposed vertex
   * @param proposedHeader results of vertex execution
   * @param timestamp timestamp to use for the vote in milliseconds since epoch
   * @param highQC our current sync state
   * @return A vote result containing the vote and any committed vertices
   */
  public Optional<Vote> voteFor(
      VerifiedVertex proposedVertex, BFTHeader proposedHeader, long timestamp, HighQC highQC) {
    Builder safetyStateBuilder = this.state.toBuilder();

    if (!checkLastVoted(proposedVertex)) {
      return Optional.empty();
    }

    if (!checkLocked(proposedVertex, safetyStateBuilder)) {
      return Optional.empty();
    }

    final Vote vote = createVote(proposedVertex, proposedHeader, timestamp, highQC);

    safetyStateBuilder.lastVote(vote);

    this.state = safetyStateBuilder.build();
    this.persistentSafetyStateStore.commitState(this.state);

    return Optional.of(vote);
  }

  public Vote timeoutVote(Vote vote) {
    if (vote.isTimeout()) { // vote is already timed out
      return vote;
    }

    final VoteTimeout voteTimeout = VoteTimeout.of(vote);
    final HashCode voteTimeoutHash = hasher.hash(voteTimeout);

    final ECDSASignature timeoutSignature = this.signer.sign(voteTimeoutHash);
    final Vote timeoutVote = vote.withTimeoutSignature(timeoutSignature);

    this.state = this.state.toBuilder().lastVote(timeoutVote).build();
    this.persistentSafetyStateStore.commitState(this.state);

    return timeoutVote;
  }

  public Vote createVote(
      VerifiedVertex proposedVertex, BFTHeader proposedHeader, long timestamp, HighQC highQC) {
    final VoteData voteData = constructVoteData(proposedVertex, proposedHeader);
    final var voteHash = Vote.getHashOfData(hasher, voteData, timestamp);

    // TODO make signing more robust by including author in signed hash
    final ECDSASignature signature = this.signer.sign(voteHash);
    return new Vote(this.self, voteData, timestamp, signature, highQC, Optional.empty());
  }

  public Optional<Vote> getLastVote(View view) {
    return this.state.getLastVote().filter(lastVote -> lastVote.getView().equals(view));
  }
}
