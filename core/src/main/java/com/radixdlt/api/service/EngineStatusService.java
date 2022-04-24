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

package com.radixdlt.api.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineReader;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.forks.CandidateForkVote;
import com.radixdlt.statecomputer.forks.ForkVotingResult;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.ForksEpochStore;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.store.LastProof;
import com.radixdlt.sync.CommittedReader;
import java.util.Optional;

public final class EngineStatusService {
  private final RadixEngine<LedgerAndBFTProof> radixEngine;
  private final CommittedReader committedReader;
  private final LedgerProof lastProof;
  private final LedgerProof lastEpochProof;
  private final Forks forks;
  private final ForksEpochStore forksEpochStore;

  private final Object cachedForksValuesCalculationLock = new Object();
  private long cachedForksValuesLastEpoch = -1;
  private short cachedCandidateForkVotingResult = 0;
  private Optional<Long> cachedUpcomingForkRemainingEpochs = Optional.empty();

  @Inject
  public EngineStatusService(
      RadixEngine<LedgerAndBFTProof> radixEngine,
      CommittedReader committedReader,
      @LastProof LedgerProof lastProof,
      @LastEpochProof LedgerProof lastEpochProof,
      Forks forks,
      ForksEpochStore forksEpochStore) {
    this.radixEngine = radixEngine;
    this.committedReader = committedReader;
    this.lastProof = lastProof;
    this.lastEpochProof = lastEpochProof;
    this.forks = forks;
    this.forksEpochStore = forksEpochStore;

    refreshForksCachedValues();
  }

  public LedgerProof getEpochProof(long epoch) {
    return committedReader.getEpochProof(epoch).orElse(lastEpochProof);
  }

  public LedgerProof getCurrentProof() {
    var ledgerAndBFTProof = radixEngine.read(RadixEngineReader::getMetadata);
    return ledgerAndBFTProof == null ? lastProof : ledgerAndBFTProof.getProof();
  }

  public Optional<Long> getCandidateForkRemainingEpochs() {
    refreshForksCachedValues();
    return this.cachedUpcomingForkRemainingEpochs;
  }

  private void refreshForksCachedValues() {
    final long currentEpoch = getCurrentProof().getEpoch();

    synchronized (cachedForksValuesCalculationLock) {
      if (currentEpoch > cachedForksValuesLastEpoch) {
        this.cachedUpcomingForkRemainingEpochs =
            calculateCandidateForkRemainingEpochs(currentEpoch);
        this.cachedCandidateForkVotingResult = calculateCandidateForkVotingResult(currentEpoch);
        this.cachedForksValuesLastEpoch = currentEpoch;
      }
    }
  }

  @VisibleForTesting
  Optional<Long> calculateCandidateForkRemainingEpochs(long currentEpoch) {
    if (forks.getCandidateFork().isEmpty()) {
      return Optional.empty();
    }

    final var candidateFork = forks.getCandidateFork().orElseThrow();

    if (forksEpochStore.getStoredForks().containsValue(candidateFork.name())) {
      // candidate fork is already executed
      return Optional.empty();
    }

    final var fromEpoch = Math.max(0, currentEpoch - candidateFork.longestThresholdEpochs());

    final var thresholdsPassingEpochs =
        Forks.calculateThresholdsPassingEpochs(
            candidateFork.thresholds(),
            forksEpochStore.forkVotingResultsCursor(
                fromEpoch,
                candidateFork.maxEpoch(),
                CandidateForkVote.candidateForkId(candidateFork)),
            currentEpoch);

    return thresholdsPassingEpochs.entrySet().stream()
        .filter(e -> e.getValue() > 0)
        .map(e -> Math.max(0, e.getKey().numEpochsBeforeEnacted() - e.getValue()))
        .min(Integer::compare)
        .flatMap(
            shortestRemainingByNumBeforeEnacted -> {
              final var expectedEpochByNumBeforeEnacted =
                  currentEpoch + shortestRemainingByNumBeforeEnacted;

              if (expectedEpochByNumBeforeEnacted > candidateFork.maxEpoch()) {
                // expected epoch by epochsBeforeEnacted will be past the maxEpoch
                return Optional.empty();
              }

              // need to consider minEpoch as well
              final var expectedEpoch =
                  Math.max(candidateFork.minEpoch(), expectedEpochByNumBeforeEnacted);

              final var remainingEpochs = expectedEpoch - currentEpoch;
              return remainingEpochs <= 0 ? Optional.empty() : Optional.of(remainingEpochs);
            });
  }

  public float getCandidateForkVotingResultPercentage() {
    refreshForksCachedValues();
    return (float) cachedCandidateForkVotingResult / 100;
  }

  private short calculateCandidateForkVotingResult(long currentEpoch) {
    if (forks.getCandidateFork().isEmpty()) {
      return 0;
    }

    final var candidateForkId =
        CandidateForkVote.candidateForkId(forks.getCandidateFork().orElseThrow());
    final var forksVotingResults = forksEpochStore.getForksVotingResultsForEpoch(currentEpoch);
    return forksVotingResults.stream()
        .filter(result -> result.candidateForkId().equals(candidateForkId))
        .map(ForkVotingResult::stakePercentageVoted)
        .findFirst()
        .orElse((short) 0);
  }
}
