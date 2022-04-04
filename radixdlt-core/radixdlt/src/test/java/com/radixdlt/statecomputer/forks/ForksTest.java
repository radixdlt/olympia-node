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

import static com.radixdlt.statecomputer.forks.RERulesVersion.OLYMPIA_V1;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.Test;

public final class ForksTest {

  private final ForksEpochStore emptyForksEpochStore =
      new InMemoryForksEpochStore(new InMemoryForksEpochStore.Store());

  @Test
  public void should_fail_when_two_forks_with_the_same_name() {
    final var fork1 =
        new FixedEpochForkConfig("fork1", OLYMPIA_V1.create(RERulesConfig.testingDefault()), 0L);
    final var fork2 =
        new FixedEpochForkConfig("fork1", OLYMPIA_V1.create(RERulesConfig.testingDefault()), 1L);
    final var forks = Set.<ForkConfig>of(fork1, fork2);

    final var exception = assertThrows(IllegalArgumentException.class, () -> Forks.create(forks));

    assertTrue(exception.getMessage().contains("duplicate name"));
  }

  @Test
  public void should_fail_when_no_genesis() {
    final var fork1 =
        new FixedEpochForkConfig("fork1", OLYMPIA_V1.create(RERulesConfig.testingDefault()), 1L);
    final var forks = Set.<ForkConfig>of(fork1);

    final var exception = assertThrows(IllegalArgumentException.class, () -> Forks.create(forks));

    assertTrue(exception.getMessage().contains("must start at epoch"));
  }

  @Test
  public void should_fail_when_duplicate_epoch() {
    final var fork1 =
        new FixedEpochForkConfig("fork1", OLYMPIA_V1.create(RERulesConfig.testingDefault()), 0L);
    final var fork2 =
        new FixedEpochForkConfig("fork2", OLYMPIA_V1.create(RERulesConfig.testingDefault()), 2L);
    final var fork3 =
        new FixedEpochForkConfig("fork3", OLYMPIA_V1.create(RERulesConfig.testingDefault()), 2L);
    final var forks = Set.<ForkConfig>of(fork1, fork2, fork3);

    final var exception = assertThrows(IllegalArgumentException.class, () -> Forks.create(forks));

    assertTrue(exception.getMessage().contains("duplicate epoch"));
  }

  @Test
  public void forks_should_respect_candidate_epoch_limits() {
    final var threshold = new CandidateForkConfig.Threshold((short) 8000, 1);
    final var candidate =
        new CandidateForkConfig(
            "candidate",
            OLYMPIA_V1.create(RERulesConfig.testingDefault()),
            ImmutableSet.of(threshold),
            3L,
            5L);

    assertFalse(
        Forks.shouldCandidateForkBeEnacted(
            candidate,
            proofForCandidate(
                1L /* next epoch = 2; minEpoch <!= 2 <= maxEpoch */,
                votesFor(2L, candidate, threshold.requiredStake())),
            emptyForksEpochStore));
    assertTrue(
        Forks.shouldCandidateForkBeEnacted(
            candidate,
            proofForCandidate(
                2L /* next epoch = 3; minEpoch <= 3 <= maxEpoch */,
                votesFor(3L, candidate, threshold.requiredStake())),
            emptyForksEpochStore));
    assertTrue(
        Forks.shouldCandidateForkBeEnacted(
            candidate,
            proofForCandidate(
                3L /* next epoch = 4; minEpoch <= 4 <= maxEpoch */,
                votesFor(4L, candidate, threshold.requiredStake())),
            emptyForksEpochStore));
    assertTrue(
        Forks.shouldCandidateForkBeEnacted(
            candidate,
            proofForCandidate(
                4L /* next epoch = 5; minEpoch <= 5 <= maxEpoch */,
                votesFor(5L, candidate, threshold.requiredStake())),
            emptyForksEpochStore));
    assertFalse(
        Forks.shouldCandidateForkBeEnacted(
            candidate,
            proofForCandidate(
                5L /* next epoch = 6; minEpoch <= 6 <!= maxEpoch */,
                votesFor(6L, candidate, threshold.requiredStake())),
            emptyForksEpochStore));
  }

  @Test
  public void forks_should_respect_candidate_required_stake() {
    final var threshold = new CandidateForkConfig.Threshold((short) 8000, 1);
    final var candidate =
        new CandidateForkConfig(
            "candidate",
            OLYMPIA_V1.create(RERulesConfig.testingDefault()),
            ImmutableSet.of(threshold),
            3L,
            5L);

    assertFalse(
        Forks.shouldCandidateForkBeEnacted(
            candidate,
            proofForCandidate(
                candidate.minEpoch() - 1,
                votesFor(
                    candidate.minEpoch(),
                    candidate,
                    (short) (threshold.requiredStake() - 1) /* too little stake */)),
            emptyForksEpochStore));

    assertTrue(
        Forks.shouldCandidateForkBeEnacted(
            candidate,
            proofForCandidate(
                candidate.minEpoch() - 1,
                votesFor(
                    candidate.minEpoch(),
                    candidate,
                    threshold.requiredStake() /* just enough stake */)),
            emptyForksEpochStore));

    assertTrue(
        Forks.shouldCandidateForkBeEnacted(
            candidate,
            proofForCandidate(
                candidate.minEpoch() - 1,
                votesFor(
                    candidate.minEpoch(),
                    candidate,
                    (short) (threshold.requiredStake() + 1) /* more than required */)),
            emptyForksEpochStore));
  }

  @Test
  public void forks_should_correctly_test_candidate_with_epochs_before_enacted() {
    final var threshold = new CandidateForkConfig.Threshold((short) 8000, 4);
    final var candidate =
        new CandidateForkConfig(
            "candidate",
            OLYMPIA_V1.create(RERulesConfig.testingDefault()),
            ImmutableSet.of(threshold),
            10L,
            20L);
    final var candidateForkId = CandidateForkVote.candidateForkId(candidate);

    final var forksEpochStore = new InMemoryForksEpochStore(new InMemoryForksEpochStore.Store());

    final var proofAtEpoch15 =
        proofForCandidate(
            15L, /* proof at epoch 15 */
            votesFor(16L /* contains votes for epoch 16 */, candidate, threshold.requiredStake()));

    assertFalse(Forks.shouldCandidateForkBeEnacted(candidate, proofAtEpoch15, forksEpochStore));

    // adding votes for epoch 15, threshold passing for 2 epochs - not enough
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(15L, candidateForkId, threshold.requiredStake()));
    assertFalse(Forks.shouldCandidateForkBeEnacted(candidate, proofAtEpoch15, forksEpochStore));

    // adding votes for epoch 14, threshold passing for 3 epochs - still not enough
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(14L, candidateForkId, threshold.requiredStake()));
    assertFalse(Forks.shouldCandidateForkBeEnacted(candidate, proofAtEpoch15, forksEpochStore));

    // adding votes for epoch 13, threshold passing for 4 epochs - just enough
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(13L, candidateForkId, threshold.requiredStake()));
    assertTrue(Forks.shouldCandidateForkBeEnacted(candidate, proofAtEpoch15, forksEpochStore));
  }

  @Test
  public void forks_should_correctly_test_candidate_with_epochs_before_enacted_with_a_gap() {
    final var threshold = new CandidateForkConfig.Threshold((short) 8000, 3);
    final var candidate =
        new CandidateForkConfig(
            "candidate",
            OLYMPIA_V1.create(RERulesConfig.testingDefault()),
            ImmutableSet.of(threshold),
            1L,
            100L);
    final var candidateForkId = CandidateForkVote.candidateForkId(candidate);

    final var forksEpochStore1 = new InMemoryForksEpochStore(new InMemoryForksEpochStore.Store());
    forksEpochStore1.storeForkVotingResult(
        new ForkVotingResult(13L, candidateForkId, threshold.requiredStake()));
    forksEpochStore1.storeForkVotingResult(
        new ForkVotingResult(14L, candidateForkId, threshold.requiredStake()));
    forksEpochStore1.storeForkVotingResult(
        new ForkVotingResult(15L, candidateForkId, (short) (threshold.requiredStake() - 1)));
    final var proofAtEpoch15 =
        proofForCandidate(
            15L, /* Proof at epoch 15 */
            votesFor(16L /* Contains votes for epoch 16 */, candidate, threshold.requiredStake()));

    // Votes for epoch 13, 14, gap at epoch 15, and then another vote at epoch 16.
    // The gap should reset the counter, so the fork shouldn't be enacted.
    assertFalse(Forks.shouldCandidateForkBeEnacted(candidate, proofAtEpoch15, forksEpochStore1));

    final var forksEpochStore2 = new InMemoryForksEpochStore(new InMemoryForksEpochStore.Store());
    forksEpochStore2.storeForkVotingResult(
        new ForkVotingResult(13L, candidateForkId, threshold.requiredStake()));
    forksEpochStore2.storeForkVotingResult(
        new ForkVotingResult(14L, candidateForkId, threshold.requiredStake()));
    forksEpochStore2.storeForkVotingResult(
        new ForkVotingResult(15L, candidateForkId, (short) (threshold.requiredStake() - 1)));
    forksEpochStore2.storeForkVotingResult(
        new ForkVotingResult(16L, candidateForkId, threshold.requiredStake()));
    forksEpochStore2.storeForkVotingResult(
        new ForkVotingResult(17L, candidateForkId, threshold.requiredStake()));
    final var proofAtEpoch17 =
        proofForCandidate(
            17L, /* Proof at epoch 17 */
            votesFor(18L /* Contains votes for epoch 18 */, candidate, threshold.requiredStake()));

    // Votes for epoch 13, 14, gap at epoch 15, and then three consecutive votes in e16, e17 and e18
    assertTrue(Forks.shouldCandidateForkBeEnacted(candidate, proofAtEpoch17, forksEpochStore2));
  }

  @Test
  public void forks_should_test_candidate_with_both_past_and_current_results() {
    final var ignoredThreshold = new CandidateForkConfig.Threshold((short) 7000, 3);
    final var threshold = new CandidateForkConfig.Threshold((short) 9000, 1);
    final var candidate =
        new CandidateForkConfig(
            "candidate",
            OLYMPIA_V1.create(RERulesConfig.testingDefault()),
            ImmutableSet.of(ignoredThreshold, threshold),
            10L,
            20L);
    final var candidateForkId = CandidateForkVote.candidateForkId(candidate);

    final var forksEpochStore = new InMemoryForksEpochStore(new InMemoryForksEpochStore.Store());

    final var proofAtEpoch15 =
        proofForCandidate(
            15L, /* proof at epoch 15 */
            votesFor(16L /* contains votes for epoch 16 */, candidate, threshold.requiredStake()));

    forksEpochStore.storeForkVotingResult(new ForkVotingResult(13L, candidateForkId, (short) 7500));
    assertTrue(Forks.shouldCandidateForkBeEnacted(candidate, proofAtEpoch15, forksEpochStore));
  }

  private LedgerAndBFTProof proofForCandidate(
      long epoch, ImmutableSet<ForkVotingResult> forksVotingResults) {
    final var ledgerProof = mock(LedgerProof.class);
    // value is not used, but optional needs to be present in proof (test for epoch boundary)
    final var validatorSet =
        BFTValidatorSet.from(Stream.of(BFTValidator.from(BFTNode.random(), UInt256.ONE)));
    when(ledgerProof.getNextValidatorSet()).thenReturn(Optional.of(validatorSet));

    when(ledgerProof.getEpoch()).thenReturn(epoch);

    return LedgerAndBFTProof.create(ledgerProof).withForksVotingResults(forksVotingResults);
  }

  private ImmutableSet<ForkVotingResult> votesFor(
      long epoch, CandidateForkConfig forkConfig, short votes) {
    return ImmutableSet.of(
        new ForkVotingResult(epoch, CandidateForkVote.candidateForkId(forkConfig), votes));
  }

  @Test
  public void forks_should_signal_ledger_inconsistency_when_db_entry_is_missing() {
    final var fork1 =
        new FixedEpochForkConfig("fork1", OLYMPIA_V1.create(RERulesConfig.testingDefault()), 0L);
    final var fork2 =
        new FixedEpochForkConfig("fork2", OLYMPIA_V1.create(RERulesConfig.testingDefault()), 10L);

    final var forks = Forks.create(Set.of(fork1, fork2));

    final var committedReader = mock(CommittedReader.class);
    final var forksEpochStore = mock(ForksEpochStore.class);

    // latest epoch is 11, so fork2 should be stored...
    final var proof = proofAtEpoch(11L);
    when(committedReader.getLastProof()).thenReturn(Optional.of(proof));

    // ...but it isn't
    when(forksEpochStore.getStoredForks()).thenReturn(ImmutableMap.of(0L, fork1.name()));

    final var exception =
        assertThrows(
            IllegalStateException.class, () -> forks.init(committedReader, forksEpochStore));

    assertTrue(exception.getMessage().toLowerCase().contains("forks inconsistency"));
  }

  @Test
  public void forks_should_signal_ledger_inconsistency_when_config_is_missing() {
    final var fork1 =
        new FixedEpochForkConfig("fork1", OLYMPIA_V1.create(RERulesConfig.testingDefault()), 0L);

    final var forks = Forks.create(Set.of(fork1));

    final var committedReader = mock(CommittedReader.class);
    final var forksEpochStore = mock(ForksEpochStore.class);

    final var proof = proofAtEpoch(11L);
    when(committedReader.getLastProof()).thenReturn(Optional.of(proof));

    when(forksEpochStore.getStoredForks())
        .thenReturn(
            ImmutableMap.of(
                0L,
                fork1.name(),
                10L,
                "fork2" /* fork2 was executed at epoch 10 according to the ledger */));

    final var exception =
        assertThrows(
            IllegalStateException.class, () -> forks.init(committedReader, forksEpochStore));

    assertTrue(exception.getMessage().toLowerCase().contains("forks inconsistency"));
  }

  @Test
  public void it_should_correctly_calculate_execute_epoch_for_a_candidate_fork() {
    final var engineRules = OLYMPIA_V1.create(RERulesConfig.testingDefault());
    final var genesis = new FixedEpochForkConfig("genesis", engineRules, 0L);
    final var candidate =
        new CandidateForkConfig(
            "candidate",
            engineRules,
            ImmutableSet.of(
                new CandidateForkConfig.Threshold((short) 8000, 4), /* 80% for 4 epochs */
                new CandidateForkConfig.Threshold((short) 9000, 2) /* or 90% for 2 epochs */),
            10 /* min epoch */,
            20 /* max epoch */);
    final var candidateForkId = CandidateForkVote.candidateForkId(candidate);
    final var sut = Forks.create(Set.of(genesis, candidate));

    /* Contains pairs of (expectedResult, input), where input is an array of pairs: (epoch, percentage_stake_voted) */
    final var testCases =
        ImmutableList.of(
            Pair.of(
                Optional.of(10L) /* Minimum epoch */,
                new Object[][] {{6L, 8000}, {7L, 8000}, {8L, 8000}, {9L, 8000}, {10L, 8000}}),
            Pair.of(
                Optional.empty(),
                new Object[][] {
                  {6L, 8000}, {7L, 8000}, {8L, 8000}, {9L, 8000}, {11L /* one epoch gap */, 8000}
                }),
            Pair.of(
                Optional.empty(),
                new Object[][] {{6L, 3000}, {7L, 3000}, {8L, 8000}, {9L, 8000}, {10L, 8000}}),
            Pair.of(Optional.of(10L), new Object[][] {{9L, 9000}, {10L, 9000}}),
            Pair.of(Optional.empty(), new Object[][] {{8L, 9000}, {9L, 9000}}),
            Pair.of(Optional.empty(), new Object[][] {{19L, 8500}, {20L, 9000}, {21L, 9000}}),
            Pair.of(Optional.of(20L), new Object[][] {{19L, 9900}, {20L, 9000}}),
            Pair.of(Optional.empty(), new Object[][] {}));

    testCases.forEach(
        pair -> {
          final var votingResults =
              Arrays.stream(pair.getSecond())
                  .map(
                      arr ->
                          new ForkVotingResult(
                              (long) arr[0], candidateForkId, (short) (int) arr[1]))
                  .toList();
          assertEquals(
              pair.getFirst(),
              sut.findExecuteEpochForCandidate(forksEpochStoreWithResults(votingResults)));
        });
  }

  private ForksEpochStore forksEpochStoreWithResults(
      Collection<ForkVotingResult> forkVotingResults) {
    final var forksEpochStore = new InMemoryForksEpochStore(new InMemoryForksEpochStore.Store());
    forkVotingResults.forEach(forksEpochStore::storeForkVotingResult);
    return forksEpochStore;
  }

  private LedgerProof proofAtEpoch(long epoch) {
    final var ledgerProof = mock(LedgerProof.class);
    when(ledgerProof.getEpoch()).thenReturn(epoch);
    return ledgerProof;
  }
}
