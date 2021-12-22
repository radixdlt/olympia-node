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

import com.google.common.hash.HashCode;
import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.liveness.VoteTimeout;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.Hasher;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Verifies signatures of BFT messages then forwards to the next processor */
@SecurityCritical({SecurityKind.SIG_VERIFY})
public final class BFTEventVerifier implements BFTEventProcessor {
  private static final Logger log = LogManager.getLogger();

  private final BFTValidatorSet validatorSet;
  private final BFTEventProcessor forwardTo;
  private final Hasher hasher;
  private final HashVerifier verifier;

  public BFTEventVerifier(
      BFTValidatorSet validatorSet,
      BFTEventProcessor forwardTo,
      Hasher hasher,
      HashVerifier verifier) {
    this.validatorSet = Objects.requireNonNull(validatorSet);
    this.hasher = Objects.requireNonNull(hasher);
    this.verifier = Objects.requireNonNull(verifier);
    this.forwardTo = forwardTo;
  }

  @Override
  public void start() {
    forwardTo.start();
  }

  @Override
  public void processViewUpdate(ViewUpdate viewUpdate) {
    forwardTo.processViewUpdate(viewUpdate);
  }

  @Override
  public void processVote(Vote vote) {
    validAuthor(vote)
        .ifPresent(
            node -> {
              boolean verifiedVoteData =
                  verifyHash(node, vote.getHashOfData(hasher), vote.getSignature(), vote);
              if (!verifiedVoteData) {
                log.warn("Ignoring invalid vote data {}", vote);
                return;
              }

              boolean verifiedTimeoutData =
                  vote.getTimeoutSignature()
                      .map(
                          timeoutSignature ->
                              verify(node, VoteTimeout.of(vote), timeoutSignature, vote))
                      .orElse(true);

              if (!verifiedTimeoutData) {
                log.warn("Ignoring invalid timeout data {}", vote);
                return;
              }

              forwardTo.processVote(vote);
            });
  }

  @Override
  public void processProposal(Proposal proposal) {
    validAuthor(proposal)
        .ifPresent(
            node -> {
              if (verify(node, proposal.getVertex(), proposal.getSignature(), proposal)) {
                forwardTo.processProposal(proposal);
              }
            });
  }

  @Override
  public void processLocalTimeout(ScheduledLocalTimeout localTimeout) {
    forwardTo.processLocalTimeout(localTimeout);
  }

  @Override
  public void processBFTUpdate(BFTInsertUpdate update) {
    forwardTo.processBFTUpdate(update);
  }

  @Override
  public void processBFTRebuildUpdate(BFTRebuildUpdate update) {
    forwardTo.processBFTRebuildUpdate(update);
  }

  private Optional<BFTNode> validAuthor(ConsensusEvent event) {
    BFTNode node = event.getAuthor();
    if (!validatorSet.containsNode(node)) {
      log.warn(
          "CONSENSUS_EVENT: {} from author {} not in validator set {}",
          event.getClass().getSimpleName(),
          node,
          this.validatorSet);
      return Optional.empty();
    }
    return Optional.of(node);
  }

  private boolean verifyHash(BFTNode author, HashCode hash, ECDSASignature signature, Object what) {
    boolean verified = this.verifier.verify(author.getKey(), hash, signature);
    if (!verified) {
      log.info("Ignoring invalid signature from {} for {}", author, what);
    }
    return verified;
  }

  private boolean verify(BFTNode author, Object hashable, ECDSASignature signature, Object what) {
    return verifyHash(author, this.hasher.hash(hashable), signature, what);
  }
}
