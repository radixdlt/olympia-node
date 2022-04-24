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

package com.radixdlt.statecomputer;

import static com.radixdlt.statecomputer.forks.RERulesVersion.OLYMPIA_V1;
import static com.radixdlt.utils.TypedMocks.rmock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.radixdlt.application.validators.state.ValidatorSystemMetadata;
import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.statecomputer.forks.CandidateForkConfig;
import com.radixdlt.statecomputer.forks.CandidateForkVote;
import com.radixdlt.statecomputer.forks.ForkVotingResult;
import com.radixdlt.statecomputer.forks.RERules;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.store.EngineStore;
import com.radixdlt.utils.Shorts;
import com.radixdlt.utils.UInt256;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.Test;

public final class CandidateForkVotesPostProcessorTest {
  private final RERules reRules = OLYMPIA_V1.create(RERulesConfig.testingDefault());
  private final CandidateForkConfig fork1 =
      new CandidateForkConfig(
          "fork1",
          reRules,
          ImmutableSet.of(new CandidateForkConfig.Threshold((short) 8000, 1)),
          1L,
          5L);
  private final HashCode fork1CandidateForkId = CandidateForkVote.candidateForkId(fork1);
  private final CandidateForkConfig fork2 =
      new CandidateForkConfig(
          "fork2",
          reRules,
          ImmutableSet.of(new CandidateForkConfig.Threshold((short) 8000, 1)),
          1L,
          5L);
  private final HashCode fork2CandidateForkid = CandidateForkVote.candidateForkId(fork2);

  private SubstateIndex<ValidatorSystemMetadata> metadataSubstateIdx =
      SubstateIndex.create(
          SubstateTypeId.VALIDATOR_SYSTEM_META_DATA.id(), ValidatorSystemMetadata.class);

  private CandidateForkVotesPostProcessor sut =
      new CandidateForkVotesPostProcessor(reRules.parser().getSubstateDeserialization());

  private LedgerAndBFTProof ledgerAndBftProof;
  private EngineStore<LedgerAndBFTProof> engineStore;

  private void setup(
      BFTValidatorSet bftValidatorSet, Map<BFTNode, CandidateForkConfig> validatorsVotes) {
    final var ledgerProof = mock(LedgerProof.class);
    when(ledgerProof.getNextValidatorSet()).thenReturn(Optional.of(bftValidatorSet));
    this.ledgerAndBftProof = LedgerAndBFTProof.create(ledgerProof);
    this.engineStore = rmock(EngineStore.class);

    final var votesSubstates =
        validatorsVotes.entrySet().stream()
            .map(e -> createVoteSubstateBytes(e.getKey(), e.getValue()))
            .toList();

    when(engineStore.openIndexedCursor(metadataSubstateIdx))
        .thenReturn(CloseableCursor.of(votesSubstates));
  }

  @Test
  public void should_ignore_non_epoch_process() {
    final var nonEpochLedgerProof = mock(LedgerProof.class);
    when(nonEpochLedgerProof.getNextValidatorSet()).thenReturn(Optional.empty());
    final var nonEpochProof = LedgerAndBFTProof.create(nonEpochLedgerProof);
    assertTrue(sut.process(nonEpochProof, null, null).getForksVotingResults().isEmpty());
  }

  @Test
  public void should_count_forks_votes_for_a_single_fork_and_equal_stake_vset()
      throws RadixEngineException {
    final var equalStakeValidatorSetOf6 =
        BFTValidatorSet.from(
            List.of(
                BFTValidator.from(bftNode(0), UInt256.ONE),
                BFTValidator.from(bftNode(1), UInt256.ONE),
                BFTValidator.from(bftNode(2), UInt256.ONE),
                BFTValidator.from(bftNode(3), UInt256.ONE),
                BFTValidator.from(bftNode(4), UInt256.ONE),
                BFTValidator.from(bftNode(5), UInt256.ONE)));

    // 3 out of 6 votes
    setup(
        equalStakeValidatorSetOf6,
        Map.of(
            bftNode(0), fork1,
            bftNode(1), fork1,
            bftNode(2), fork1));

    processAndAssert(
        result -> {
          assertEquals(1, result.size());
          assertEquals((short) 5000, result.iterator().next().stakePercentageVoted());
        });

    // 6 out of 6 votes
    setup(
        equalStakeValidatorSetOf6,
        Map.of(
            bftNode(0), fork1,
            bftNode(1), fork1,
            bftNode(2), fork1,
            bftNode(3), fork1,
            bftNode(4), fork1,
            bftNode(5), fork1));

    processAndAssert(
        result -> {
          final var next = result.iterator().next();
          assertEquals(1, result.size());
          assertEquals((short) 10000, next.stakePercentageVoted());
          assertEquals(fork1CandidateForkId, next.candidateForkId());
        });

    // no votes
    setup(equalStakeValidatorSetOf6, Map.of());
    processAndAssert(result -> assertEquals(0, result.size()));

    // 2 out of 6 votes (below the threshold to store)
    setup(
        equalStakeValidatorSetOf6,
        Map.of(
            bftNode(0), fork1,
            bftNode(1), fork1));
    processAndAssert(result -> assertEquals(0, result.size()));
  }

  @Test
  public void should_count_votes_for_two_forks() throws RadixEngineException {
    final var equalStakeValidatorSetOf6 =
        BFTValidatorSet.from(
            List.of(
                BFTValidator.from(bftNode(0), UInt256.ONE),
                BFTValidator.from(bftNode(1), UInt256.ONE),
                BFTValidator.from(bftNode(2), UInt256.ONE),
                BFTValidator.from(bftNode(3), UInt256.ONE),
                BFTValidator.from(bftNode(4), UInt256.ONE),
                BFTValidator.from(bftNode(5), UInt256.ONE)));

    // 3 validators vote for fork1 and 3 vote for fork2
    setup(
        equalStakeValidatorSetOf6,
        Map.of(
            bftNode(0), fork1,
            bftNode(1), fork1,
            bftNode(2), fork1,
            bftNode(3), fork2,
            bftNode(4), fork2,
            bftNode(5), fork2));

    processAndAssert(
        result -> {
          assertEquals(2, result.size());
          final var resultFork1 =
              result.stream()
                  .filter(e -> e.candidateForkId().equals(fork1CandidateForkId))
                  .findAny()
                  .orElseThrow();
          final var resultFork2 =
              result.stream()
                  .filter(e -> e.candidateForkId().equals(fork2CandidateForkid))
                  .findAny()
                  .orElseThrow();

          assertEquals((short) 5000, resultFork1.stakePercentageVoted());
          assertEquals((short) 5000, resultFork2.stakePercentageVoted());
        });
  }

  @Test
  public void should_correctly_count_votes_for_different_validator_sets()
      throws RadixEngineException {
    final var singleNodeVset =
        BFTValidatorSet.from(List.of(BFTValidator.from(bftNode(0), UInt256.ONE)));

    setup(singleNodeVset, Map.of(bftNode(0), fork1));

    processAndAssert(
        result -> {
          assertEquals(1, result.size());
          assertEquals(
              (short) 10000,
              result.stream()
                  .filter(r -> r.candidateForkId().equals(fork1CandidateForkId))
                  .findAny()
                  .orElseThrow()
                  .stakePercentageVoted());
        });

    final var twoNodesDifferentStakeVset =
        BFTValidatorSet.from(
            List.of(
                BFTValidator.from(bftNode(0), UInt256.TEN),
                BFTValidator.from(bftNode(1), UInt256.ONE)));

    // 1/11 of stake
    setup(twoNodesDifferentStakeVset, Map.of(bftNode(1), fork1));

    // empty, below 50% threshold
    processAndAssert(result -> assertEquals(0, result.size()));

    // 10/11 of stake
    setup(twoNodesDifferentStakeVset, Map.of(bftNode(0), fork1));

    processAndAssert(
        result -> {
          assertEquals(1, result.size());
          assertEquals(
              (short) 9090,
              result.stream()
                  .filter(r -> r.candidateForkId().equals(fork1CandidateForkId))
                  .findAny()
                  .orElseThrow()
                  .stakePercentageVoted());
        });
  }

  @Test
  public void should_ignore_votes_from_nodes_outside_the_validator_set()
      throws RadixEngineException {
    final var validatorSet =
        BFTValidatorSet.from(
            List.of(
                BFTValidator.from(bftNode(0), UInt256.ONE),
                BFTValidator.from(bftNode(1), UInt256.ONE),
                BFTValidator.from(bftNode(2), UInt256.ONE)));

    // a vote from a single node in a validator set and two other nodes
    setup(
        validatorSet,
        Map.of(
            bftNode(0), fork1,
            bftNode(3 /* not in the validator set */), fork1,
            bftNode(4 /* not in the validator set */), fork1));

    // empty, below 50% threshold
    processAndAssert(result -> assertEquals(0, result.size()));

    // votes from two nodes in a validator set and two other nodes
    setup(
        validatorSet,
        Map.of(
            bftNode(0), fork1,
            bftNode(1), fork1,
            bftNode(3 /* not in the validator set */), fork1,
            bftNode(4 /* not in the validator set */), fork1));

    processAndAssert(
        result -> {
          assertEquals(1, result.size());
          assertEquals(
              (short) 6666,
              result.stream()
                  .filter(r -> r.candidateForkId().equals(fork1CandidateForkId))
                  .findAny()
                  .orElseThrow()
                  .stakePercentageVoted());
        });
  }

  private BFTNode bftNode(int seed) {
    return BFTNode.create(ECKeyPair.fromSeed(Shorts.toByteArray((short) seed)).getPublicKey());
  }

  private void processAndAssert(Consumer<ImmutableSet<ForkVotingResult>> consumer)
      throws RadixEngineException {
    engineStore.transaction(
        engineStoreInTransaction -> {
          consumer.accept(
              sut.process(ledgerAndBftProof, engineStoreInTransaction, List.of())
                  .getForksVotingResults()
                  .orElseThrow());
          return null;
        });
  }

  private RawSubstateBytes createVoteSubstateBytes(BFTNode node, CandidateForkConfig forkConfig) {
    final var serialized =
        reRules
            .serialization()
            .serialize(
                new ValidatorSystemMetadata(
                    node.getKey(),
                    CandidateForkVote.create(node.getKey(), forkConfig).payload().asBytes()));
    return new RawSubstateBytes(new byte[] {}, serialized);
  }
}
