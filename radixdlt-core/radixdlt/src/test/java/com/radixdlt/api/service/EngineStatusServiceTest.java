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

import static com.radixdlt.statecomputer.forks.RERulesVersion.OLYMPIA_V1;
import static com.radixdlt.utils.TypedMocks.rmock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.forks.CandidateForkConfig;
import com.radixdlt.statecomputer.forks.CandidateForkConfig.Threshold;
import com.radixdlt.statecomputer.forks.CandidateForkVote;
import com.radixdlt.statecomputer.forks.FixedEpochForkConfig;
import com.radixdlt.statecomputer.forks.ForkVotingResult;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.InMemoryForksEpochStore;
import com.radixdlt.statecomputer.forks.RERules;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.sync.CommittedReader;
import java.util.Set;
import org.junit.Test;

public final class EngineStatusServiceTest {
  private final RERules reRules = OLYMPIA_V1.create(RERulesConfig.testingDefault());

  private final FixedEpochForkConfig genesisFork = new FixedEpochForkConfig("genesis", reRules, 0L);

  private final Threshold threshold1 = new Threshold((short) 8000, 10);
  private final Threshold threshold2 = new Threshold((short) 9000, 5);

  private final CandidateForkConfig candidateFork =
      new CandidateForkConfig(
          "candidate", reRules, ImmutableSet.of(threshold1, threshold2), 40, 80);
  private final HashCode candidateForkId = CandidateForkVote.candidateForkId(candidateFork);
  private final Forks forks = Forks.create(Set.of(genesisFork, candidateFork));

  private EngineStatusService sut;
  private InMemoryForksEpochStore forksEpochStore;

  private void setup(long currentEpoch) {
    final RadixEngine<LedgerAndBFTProof> radixEngine = rmock(RadixEngine.class);
    final var committedReader = mock(CommittedReader.class);
    final var lastProof = mock(LedgerProof.class);
    when(lastProof.getEpoch()).thenReturn(currentEpoch);

    forksEpochStore = new InMemoryForksEpochStore(new InMemoryForksEpochStore.Store());
    sut =
        new EngineStatusService(
            radixEngine, committedReader, lastProof, lastProof, forks, forksEpochStore);
  }

  @Test
  public void test_no_votes() {
    final var currentEpoch = 50;
    setup(currentEpoch);
    assertTrue(sut.calculateCandidateForkRemainingEpochs(currentEpoch).isEmpty());
  }

  @Test
  public void test_votes_before_min_epoch() {
    final var currentEpoch = 30;
    setup(currentEpoch);

    // we've got 2 epochs for threshold2 (5 required)
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch, candidateForkId, threshold2.requiredStake()));
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch - 1, candidateForkId, threshold2.requiredStake()));

    // but since the minEpoch is in 10 epochs (40-30), that's the remaining epochs value
    assertEquals(
        10L, sut.calculateCandidateForkRemainingEpochs(currentEpoch).orElseThrow().longValue());

    // if there's a gap though, than minEpoch doesn't matter
    setup(currentEpoch);

    // no votes for currentEpoch
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch - 1, candidateForkId, threshold2.requiredStake()));

    assertTrue(sut.calculateCandidateForkRemainingEpochs(currentEpoch).isEmpty());
  }

  @Test
  public void test_votes_after_max_epoch() {
    final var currentEpoch = 75;
    setup(currentEpoch);
    // we've got two votes for threshold1 but it requires 10 epochs to be enacted...
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch, candidateForkId, threshold1.requiredStake()));
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch - 1, candidateForkId, threshold1.requiredStake()));

    // ...and that's after the maxEpoch
    assertTrue(sut.calculateCandidateForkRemainingEpochs(currentEpoch).isEmpty());

    // but if we have 2 votes on the threshold2, it can still happen
    setup(currentEpoch);

    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch, candidateForkId, threshold2.requiredStake()));
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch - 1, candidateForkId, threshold2.requiredStake()));

    assertEquals(
        3L, sut.calculateCandidateForkRemainingEpochs(currentEpoch).orElseThrow().longValue());
  }

  @Test
  public void test_votes_gap() {
    final var currentEpoch = 50L;
    setup(currentEpoch);
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch, candidateForkId, threshold1.requiredStake()));
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch - 1, candidateForkId, threshold1.requiredStake()));
    // no votes at currentEpoch - 2
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch - 3, candidateForkId, threshold2.requiredStake()));

    assertEquals(
        8L, sut.calculateCandidateForkRemainingEpochs(currentEpoch).orElseThrow().longValue());
  }

  @Test
  public void test_votes_gap_before_current_epoch() {
    final var currentEpoch = 50L;
    setup(currentEpoch);
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch - 1, candidateForkId, threshold2.requiredStake()));
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch - 2, candidateForkId, threshold2.requiredStake()));
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch - 3, candidateForkId, threshold2.requiredStake()));

    // no vote at current epoch == empty
    assertTrue(sut.calculateCandidateForkRemainingEpochs(currentEpoch).isEmpty());
  }

  @Test
  public void test_vote_decreased_gap_before_current_epoch() {
    final var currentEpoch = 50L;
    setup(currentEpoch);
    // too little stake votes at current epoch
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch, candidateForkId, (short) 6000));
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch - 1, candidateForkId, threshold2.requiredStake()));
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch - 2, candidateForkId, threshold2.requiredStake()));
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch - 3, candidateForkId, threshold2.requiredStake()));

    assertTrue(sut.calculateCandidateForkRemainingEpochs(currentEpoch).isEmpty());
  }

  @Test
  public void test_expected_remaining_epochs() {
    final var currentEpoch = 50L;
    setup(currentEpoch);
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch, candidateForkId, threshold2.requiredStake()));
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch - 1, candidateForkId, threshold2.requiredStake()));
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch - 2, candidateForkId, threshold2.requiredStake()));
    forksEpochStore.storeForkVotingResult(
        new ForkVotingResult(currentEpoch - 3, candidateForkId, threshold2.requiredStake()));

    // 5 epochs required - 4 epochs (including the current one) with votes == 1 remaining epoch
    assertEquals(
        1L, sut.calculateCandidateForkRemainingEpochs(currentEpoch).orElseThrow().longValue());
  }
}
