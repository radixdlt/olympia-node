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

package com.radixdlt.statecomputer.forks;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.NextCandidateForkPostProcessor;
import com.radixdlt.statecomputer.NextFixedEpochForkPostProcessor;
import com.radixdlt.sync.CommittedReader;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A container for forks. There are two kinds of forks: - a list forks executed at fixed epochs - an
 * optional candidate fork that is switched on based on a predicate (in most cases stake voting)
 *
 * <p>All forks must be executed in order, and a candidate fork can only be considered when we're
 * already running the latest fixed epoch fork.
 */
public final class Forks {
  private static final Logger log = LogManager.getLogger();

  private final ImmutableList<FixedEpochForkConfig> fixedEpochForks;
  private final Optional<CandidateForkConfig> maybeCandidateFork;

  private ForksEpochStore forksEpochStore;

  public static Forks create(Set<ForkConfig> forks) {
    if (!ensureUniqueNames(forks)) {
      throw new IllegalArgumentException("Forks contain duplicate names: " + forks);
    }

    final var candidateForks =
        forks.stream()
            .filter(CandidateForkConfig.class::isInstance)
            .map(CandidateForkConfig.class::cast)
            .collect(ImmutableList.toImmutableList());

    if (candidateForks.size() > 1) {
      throw new IllegalArgumentException(
          "Only a single candidate fork is allowed but got " + candidateForks);
    }

    final var maybeCandidateFork = candidateForks.stream().findAny();

    final var fixedEpochForks =
        forks.stream()
            .filter(FixedEpochForkConfig.class::isInstance)
            .map(FixedEpochForkConfig.class::cast)
            .sorted((a, b) -> (int) (a.epoch() - b.epoch()))
            .collect(ImmutableList.toImmutableList());

    if (fixedEpochForks.isEmpty()) {
      throw new IllegalArgumentException("At least one fork config at fixed epoch is required");
    }

    if (fixedEpochForks.get(0).epoch() != 0L) {
      throw new IllegalArgumentException("Genesis fork must start at epoch 0");
    }

    if (!sanityCheckFixedEpochs(fixedEpochForks)) {
      throw new IllegalArgumentException("Invalid forks: duplicate epoch. " + fixedEpochForks);
    }

    final var latestFixedEpochFork = fixedEpochForks.get(fixedEpochForks.size() - 1);

    if (maybeCandidateFork.isPresent()
        && maybeCandidateFork.get().minEpoch() <= latestFixedEpochFork.epoch()) {
      throw new IllegalArgumentException(
          "Candidate fork's minEpoch must be greater than the last fixed fork epoch.");
    }

    return new Forks(fixedEpochForks, maybeCandidateFork);
  }

  private static boolean ensureUniqueNames(Set<ForkConfig> forks) {
    final var namesWithoutDuplicates =
        forks.stream().map(ForkConfig::name).collect(Collectors.toSet());
    return forks.size() == namesWithoutDuplicates.size();
  }

  private static boolean sanityCheckFixedEpochs(ImmutableList<FixedEpochForkConfig> forkConfigs) {
    FixedEpochForkConfig prev = null;
    for (var i = forkConfigs.iterator(); i.hasNext(); ) {
      final var el = i.next();
      if (prev != null && prev.epoch() >= el.epoch()) {
        return false;
      }
      prev = el;
    }
    return true;
  }

  private Forks(
      ImmutableList<FixedEpochForkConfig> fixedEpochForks,
      Optional<CandidateForkConfig> maybeCandidateFork) {
    // For each given fork ruleset F1, if there's a following fork F2, we add a post processor to F1
    //   to monitor for the condition to F2, and mark that transition on the LedgerAndBFTProof
    this.fixedEpochForks =
        IntStream.range(0, fixedEpochForks.size())
            .mapToObj(
                idx -> {
                  final var forkConfig = fixedEpochForks.get(idx);
                  if (idx < fixedEpochForks.size() - 1) {
                    final var nextForkPostProcessor =
                        new NextFixedEpochForkPostProcessor(fixedEpochForks.get(idx + 1));
                    return forkConfig.addPostProcessor(nextForkPostProcessor);
                  } else if (maybeCandidateFork.isPresent()) {
                    final var nextForkPostProcessor =
                        new NextCandidateForkPostProcessor(
                            maybeCandidateFork.get(), () -> forksEpochStore);
                    return forkConfig.addPostProcessor(nextForkPostProcessor);
                  } else {
                    return forkConfig;
                  }
                })
            .collect(ImmutableList.toImmutableList());

    this.maybeCandidateFork = maybeCandidateFork;
  }

  public Optional<CandidateForkConfig> getCandidateFork() {
    return this.maybeCandidateFork;
  }

  public ImmutableList<ForkConfig> forkConfigs() {
    final var builder = ImmutableList.<ForkConfig>builder().addAll(fixedEpochForks);
    maybeCandidateFork.ifPresent(builder::add);
    return builder.build();
  }

  @SuppressWarnings("unchecked")
  public Optional<ForkConfig> getByName(String name) {
    final var maybeFixedEpochFork =
        this.fixedEpochForks.stream()
            .filter(forkConfig -> forkConfig.name().equals(name))
            .findFirst();

    if (maybeFixedEpochFork.isPresent()) {
      // Thank you Java for a non-covariant Optional type...
      return (Optional) maybeFixedEpochFork;
    } else {
      return (Optional) maybeCandidateFork.filter(forkConfig -> forkConfig.name().equals(name));
    }
  }

  public ForkConfig genesisFork() {
    return this.fixedEpochForks.get(0);
  }

  public ForkConfig newestFork() {
    if (maybeCandidateFork.isPresent()) {
      return maybeCandidateFork.get();
    } else {
      return fixedEpochForks.get(fixedEpochForks.size() - 1);
    }
  }

  public void init(CommittedReader committedReader, ForksEpochStore forksEpochStore) {
    this.forksEpochStore = forksEpochStore;
    final var initialStoredForks = forksEpochStore.getStoredForks();
    final var currentEpoch = committedReader.getLastProof().map(LedgerProof::getEpoch).orElse(0L);

    log.info(
        "Forks init [stored_forks: {}, configured_forks: {}]", initialStoredForks, forkConfigs());

    executeMissedFixedEpochForks(initialStoredForks, currentEpoch, forksEpochStore);
    executeAndCheckMissedCandidateFork(initialStoredForks, forksEpochStore);

    sanityCheckStoredForksAgainstConfiguration(forksEpochStore, currentEpoch);
  }

  private void executeMissedFixedEpochForks(
      ImmutableMap<Long, String> storedForks, long currentEpoch, ForksEpochStore forksEpochStore) {
    fixedEpochForks.forEach(
        fixedEpochFork -> {
          final var forkAlreadyStored =
              storedForks.entrySet().stream()
                  .anyMatch(e -> e.getValue().equals(fixedEpochFork.name()));

          // Simply store the fork if not already in the database,
          //   we do not check if the epoch matches here, that'll be caught by sanityCheck
          if (currentEpoch >= fixedEpochFork.epoch() && !forkAlreadyStored) {
            log.info(
                "Found a missed fork config {}, inserting at epoch {}",
                fixedEpochFork.name(),
                fixedEpochFork);
            forksEpochStore.storeForkAtEpoch(fixedEpochFork.epoch(), fixedEpochFork.name());
          }
        });
  }

  private void executeAndCheckMissedCandidateFork(
      ImmutableMap<Long, String> storedForks, ForksEpochStore forksEpochStore) {
    if (maybeCandidateFork.isEmpty()) {
      return;
    }

    final var candidateFork = maybeCandidateFork.get();

    final var maybeCandidateForkEpoch = findExecuteEpochForCandidate(forksEpochStore);
    final var maybeStoredCandidate =
        storedForks.entrySet().stream()
            .filter(e -> e.getValue().equals(candidateFork.name()))
            .findAny();

    maybeCandidateForkEpoch.ifPresentOrElse(
        executeEpoch -> {
          if (maybeStoredCandidate.isPresent()) {
            if (!maybeStoredCandidate.get().getKey().equals(executeEpoch)) {
              throw new IllegalStateException(
                  String.format(
                      "Forks inconsistency! Candidate fork should have been executed at epoch %s,"
                          + " but was at %s.",
                      executeEpoch, maybeStoredCandidate.get().getKey()));
            }
          } else {
            log.info(
                "Found a missed candidate fork config {}, inserting at epoch {}",
                candidateFork.name(),
                executeEpoch);
            forksEpochStore.storeForkAtEpoch(executeEpoch, candidateFork.name());
          }
        },
        () -> {
          if (maybeStoredCandidate.isPresent()) {
            throw new IllegalStateException(
                String.format(
                    "Forks inconsistency! Candidate fork shouldn't have been executed but was at"
                        + " epoch %s.",
                    maybeStoredCandidate.get().getKey()));
          }
        });
  }

  @VisibleForTesting
  Optional<Long> findExecuteEpochForCandidate(ForksEpochStore forksEpochStore) {
    if (maybeCandidateFork.isEmpty()) {
      return Optional.empty();
    }

    final var candidateFork = maybeCandidateFork.get();
    final var candidateForkId = CandidateForkVote.candidateForkId(candidateFork);

    final var fromEpoch = Math.max(0, candidateFork.minEpoch() - candidateFork.longestThresholdEpochs());
    final var toEpoch =
        candidateFork.maxEpoch() + 1 < candidateFork.maxEpoch() // Check for overflows
            ? Long.MAX_VALUE
            : candidateFork.maxEpoch() + 1;
    try (final var forkVotingResultsCursor =
        forksEpochStore.forkVotingResultsCursor(fromEpoch, toEpoch, candidateForkId)) {

      if (!forkVotingResultsCursor.hasNext()) {
        return Optional.empty();
      }

      final var thresholdEpochsMap =
          new HashMap<>(
              candidateFork.thresholds().stream().collect(Collectors.toMap(el -> el, el -> 0)));
      Optional<Long> previousEpoch = Optional.empty();
      do {
        final ForkVotingResult next = forkVotingResultsCursor.next();
        if (previousEpoch.isEmpty() || previousEpoch.get() + 1 == next.epoch()) {
          // There's no gap: increment the threshold if it passes, or reset back to 0
          thresholdEpochsMap.replaceAll(
              (threshold, numEpochs) ->
                  next.stakePercentageVoted() >= threshold.requiredStake()
                      ? numEpochs + 1 // threshold passes: increment numEpochs
                      : 0); // threshold doesn't pass: reset to 0
        } else {
          // There's a gap in fork voting results: re-initialize the counters
          thresholdEpochsMap.replaceAll(
              (threshold, numEpochs) ->
                  next.stakePercentageVoted() >= threshold.requiredStake() ? 1 : 0);
        }

        if (next.epoch() >= candidateFork.minEpoch()
            && next.epoch() <= candidateFork.maxEpoch()
            && thresholdEpochsMap.entrySet().stream()
                .anyMatch(e -> e.getValue() >= e.getKey().numEpochsBeforeEnacted())) {
          return Optional.of(next.epoch());
        }

        previousEpoch = Optional.of(next.epoch());
      } while (forkVotingResultsCursor.hasNext());

      return Optional.empty();
    }
  }

  private void sanityCheckStoredForksAgainstConfiguration(
      ForksEpochStore forksEpochStore, long currentEpoch) {
    final var storedForks = forksEpochStore.getStoredForks();

    fixedEpochForks.stream()
        .filter(f -> f.epoch() <= currentEpoch && f.epoch() > 0)
        .forEach(
            fork -> {
              final var maybeStored = Optional.ofNullable(storedForks.get(fork.epoch()));
              if (maybeStored.isEmpty() || !maybeStored.get().equals(fork.name())) {
                throw new IllegalStateException(
                    String.format(
                        "Forks inconsistency! Fork %s should have been executed at epoch %s, but"
                            + " wasn't.",
                        fork.name(), fork.epoch()));
              }
            });

    final var fixedEpochForksMap =
        fixedEpochForks.stream()
            .collect(ImmutableMap.toImmutableMap(FixedEpochForkConfig::epoch, Function.identity()));
    storedForks.forEach(
        (key, value) -> verifyStoredFork(fixedEpochForksMap, currentEpoch, key, value));
  }

  private void verifyStoredFork(
      ImmutableMap<Long, FixedEpochForkConfig> fixedEpochForksMap,
      long currentEpoch,
      long forkEpoch,
      String forkName) {
    if (forkEpoch > currentEpoch) {
      throw new IllegalStateException(
          String.format(
              "Illegal state: fork %s executed epoch is %s, but current epoch is %s",
              forkName, forkEpoch, currentEpoch));
    }

    // We check that the stored fork either matches a fixed epoch fork, or could be the execution of
    // the candidate fork
    final var maybeExpectedAtFixedEpoch = Optional.ofNullable(fixedEpochForksMap.get(forkEpoch));

    final var maybeExpectedCandidate =
        maybeCandidateFork.filter(f -> forkEpoch >= f.minEpoch() && forkEpoch <= f.maxEpoch());

    final var expectedAtFixedEpochMatches =
        maybeExpectedAtFixedEpoch.isPresent()
            && maybeExpectedAtFixedEpoch.get().name().equals(forkName);

    final var expectedCandidateMatches =
        maybeExpectedCandidate.isPresent() && maybeExpectedCandidate.get().name().equals(forkName);

    if (!expectedAtFixedEpochMatches && !expectedCandidateMatches) {
      throw new IllegalStateException(
          String.format(
              "Forks inconsistency! Fork %s was executed at epoch %s, but shouldn't have"
                  + " been.",
              forkName, forkEpoch));
    }
  }

  public static boolean shouldCandidateForkBeEnacted(
      CandidateForkConfig candidateFork,
      LedgerAndBFTProof ledgerAndBFTProof,
      ForksEpochStore forksEpochStore) {
    if (ledgerAndBFTProof.getForksVotingResults().isEmpty()) {
      return false;
    }

    if (ledgerAndBFTProof.getProof().getNextValidatorSet().isEmpty()) {
      return false;
    }

    final var nextEpoch = ledgerAndBFTProof.getProof().getEpoch() + 1;
    final var candidateForkId = CandidateForkVote.candidateForkId(candidateFork);

    final var maybeCurrentForkVotingResult =
        ledgerAndBFTProof.getForksVotingResults().orElseThrow().stream()
            .filter(votingResult -> votingResult.candidateForkId().equals(candidateForkId))
            .findAny();

    if (maybeCurrentForkVotingResult.isEmpty()) {
      // there are no votes for the fork in the current epoch, so it can't test positive
      return false;
    }

    final var fromEpoch = Math.max(0, nextEpoch - candidateFork.longestThresholdEpochs());
    try (final var previousVotingResultsCursor =
        forksEpochStore.forkVotingResultsCursor(
            fromEpoch, candidateFork.maxEpoch(), candidateForkId)) {
      final var previousAndCurrentResultsCursor =
          previousVotingResultsCursor.concat(
              () -> CloseableCursor.single(maybeCurrentForkVotingResult.orElseThrow()));
      return shouldCandidateForkBeEnacted(
          candidateFork, ledgerAndBFTProof.getProof(), previousAndCurrentResultsCursor);
    }
  }

  private static boolean shouldCandidateForkBeEnacted(
      CandidateForkConfig candidateFork,
      LedgerProof ledgerProof,
      CloseableCursor<ForkVotingResult> forkVotingResultsCursor) {
    final var nextEpoch = ledgerProof.getEpoch() + 1;
    if (!forkWithinAllowedEpochRange(nextEpoch, candidateFork)) {
      return false;
    }

    final var thresholdsPassingEpochs =
        calculateThresholdsPassingEpochs(
            candidateFork.thresholds(), forkVotingResultsCursor, nextEpoch);

    // At least one threshold has enough epochs
    return thresholdsPassingEpochs.entrySet().stream()
        .anyMatch(e -> e.getValue() >= e.getKey().numEpochsBeforeEnacted());
  }

  public static ImmutableMap<CandidateForkConfig.Threshold, Integer>
      calculateThresholdsPassingEpochs(
          ImmutableSet<CandidateForkConfig.Threshold> thresholds,
          CloseableCursor<ForkVotingResult> forkVotingResultsCursor,
          long atEpoch) {
    final var emptyResult =
        thresholds.stream().collect(ImmutableMap.toImmutableMap(e -> e, e -> 0));
    if (!forkVotingResultsCursor.hasNext()) {
      return emptyResult;
    }

    final var initialForkVotingResult = forkVotingResultsCursor.next();

    final var thresholdEpochsMap =
        new HashMap<>(
            thresholds.stream()
                .collect(
                    Collectors.toMap(
                        el -> el,
                        el ->
                            initialForkVotingResult.stakePercentageVoted() >= el.requiredStake()
                                ? 1
                                : 0)));

    ForkVotingResult next = initialForkVotingResult;
    long previousEpoch;
    while (forkVotingResultsCursor.hasNext() && next.epoch() <= atEpoch) {
      previousEpoch = next.epoch();
      next = forkVotingResultsCursor.next();
      final var finalNextForClosure = next;

      if (next.epoch() != previousEpoch + 1) {
        // There's a gap in fork voting results: re-initialize the counters
        thresholdEpochsMap.replaceAll(
            (threshold, numEpochs) ->
                finalNextForClosure.stakePercentageVoted() >= threshold.requiredStake() ? 1 : 0);
      } else {
        // There's no gap: increment the threshold if it passes, or reset back to 0
        thresholdEpochsMap.replaceAll(
            (threshold, numEpochs) ->
                finalNextForClosure.stakePercentageVoted() >= threshold.requiredStake()
                    ? numEpochs + 1 // threshold passes: increment numEpochs
                    : 0); // threshold doesn't pass: reset to 0
      }
    }

    if (next.epoch() == atEpoch) {
      // The cursor has finished at the correct epoch (there's no gap between the epoch and the
      // latest result)
      return ImmutableMap.copyOf(thresholdEpochsMap);
    } else {
      // There was a gap, so reset all thresholds to 0 (just return an empty result)
      return emptyResult;
    }
  }

  private static boolean forkWithinAllowedEpochRange(
      long nextEpoch, CandidateForkConfig candidateFork) {
    return nextEpoch >= candidateFork.minEpoch() && nextEpoch <= candidateFork.maxEpoch();
  }
}
