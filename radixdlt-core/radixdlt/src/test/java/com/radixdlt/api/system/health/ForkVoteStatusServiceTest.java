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

package com.radixdlt.api.system.health;

import static com.radixdlt.api.system.health.ForkVoteStatusService.ForkVoteStatus.NO_ACTION_NEEDED;
import static com.radixdlt.api.system.health.ForkVoteStatusService.ForkVoteStatus.VOTE_REQUIRED;
import static com.radixdlt.utils.TypedMocks.rmock;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.application.validators.state.ValidatorSystemMetadata;
import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.forks.CandidateForkConfig;
import com.radixdlt.statecomputer.forks.CandidateForkVote;
import com.radixdlt.statecomputer.forks.CurrentForkView;
import com.radixdlt.statecomputer.forks.FixedEpochForkConfig;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.RERules;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RERulesVersion;
import com.radixdlt.store.EngineStore;
import java.util.Arrays;
import java.util.Set;
import org.junit.Test;

public final class ForkVoteStatusServiceTest {

  private final RERules reRules = RERulesVersion.OLYMPIA_V1.create(RERulesConfig.testingDefault());

  @Test
  public void should_correctly_tell_if_fork_vote_is_needed() {
    final var self = BFTNode.random();
    final var otherNode = BFTNode.random();
    @SuppressWarnings("unchecked")
    final var engineStore = (EngineStore<LedgerAndBFTProof>) rmock(EngineStore.class);
    final var initialFork = new FixedEpochForkConfig("fork1", reRules, 0L);
    final var candidateFork =
        new CandidateForkConfig(
            "fork2",
            reRules,
            ImmutableSet.of(new CandidateForkConfig.Threshold((short) 5100, 1)),
            2L,
            Long.MAX_VALUE);
    final var forks = Forks.create(Set.of(initialFork, candidateFork));

    final var currentForkView = mock(CurrentForkView.class);
    when(currentForkView.currentForkConfig()).thenReturn(initialFork);

    when(engineStore.openIndexedCursor(any()))
        .thenAnswer(unused -> votesOf(candidateFork, otherNode));

    final var forkVoteStatusService =
        new ForkVoteStatusService(self, engineStore, forks, currentForkView);

    assertEquals(VOTE_REQUIRED, forkVoteStatusService.forkVoteStatus());

    when(engineStore.openIndexedCursor(any()))
        .thenAnswer(unused -> votesOf(candidateFork, self, otherNode));
    assertEquals(NO_ACTION_NEEDED, forkVoteStatusService.forkVoteStatus());
  }

  @Test
  public void should_not_require_a_vote_for_non_candidate_fork() {
    final var self = BFTNode.random();
    @SuppressWarnings("unchecked")
    final var engineStore = (EngineStore<LedgerAndBFTProof>) rmock(EngineStore.class);
    final var initialFork = new FixedEpochForkConfig("fork1", reRules, 0L);
    final var nextFork = new FixedEpochForkConfig("fork2", reRules, 2L);
    final var forks = Forks.create(Set.of(initialFork, nextFork));

    final var currentForkView = mock(CurrentForkView.class);
    when(currentForkView.currentForkConfig()).thenReturn(initialFork);

    final var forkVoteStatusService =
        new ForkVoteStatusService(self, engineStore, forks, currentForkView);

    assertEquals(NO_ACTION_NEEDED, forkVoteStatusService.forkVoteStatus());
    verifyNoInteractions(engineStore);
  }

  private CloseableCursor<RawSubstateBytes> votesOf(
      CandidateForkConfig forkConfig, BFTNode... nodes) {
    return CloseableCursor.of(Arrays.stream(nodes).map(n -> voteOf(n, forkConfig)).toList());
  }

  private RawSubstateBytes voteOf(BFTNode validator, CandidateForkConfig forkConfig) {
    final var pubKey = validator.getKey();
    final var substate =
        new ValidatorSystemMetadata(
            pubKey, CandidateForkVote.create(pubKey, forkConfig).payload().asBytes());
    final var serializedSubstate = reRules.serialization().serialize(substate);
    return new RawSubstateBytes(new byte[] {}, serializedSubstate);
  }
}
