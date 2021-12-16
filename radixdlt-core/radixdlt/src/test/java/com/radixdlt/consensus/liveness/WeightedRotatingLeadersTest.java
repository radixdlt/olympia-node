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

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.utils.KeyComparator;
import com.radixdlt.utils.UInt256;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.Test;

public class WeightedRotatingLeadersTest {
  private WeightedRotatingLeaders weightedRotatingLeaders;
  private WeightedRotatingLeaders weightedRotatingLeaders2;
  private ImmutableList<BFTValidator> validatorsInOrder;

  private void setUp(int validatorSetSize, int sizeOfCache) {
    this.validatorsInOrder =
        Stream.generate(() -> ECKeyPair.generateNew().getPublicKey())
            .limit(validatorSetSize)
            .map(BFTNode::create)
            .map(node -> BFTValidator.from(node, UInt256.ONE))
            .sorted(
                Comparator.comparing(
                    v -> v.getNode().getKey(), KeyComparator.instance().reversed()))
            .collect(ImmutableList.toImmutableList());

    BFTValidatorSet validatorSet = BFTValidatorSet.from(validatorsInOrder);
    this.weightedRotatingLeaders = new WeightedRotatingLeaders(validatorSet, sizeOfCache);
    this.weightedRotatingLeaders2 = new WeightedRotatingLeaders(validatorSet, sizeOfCache);
  }

  @Test
  public void when_equivalent_leaders__then_leaders_are_round_robined_deterministically() {
    for (int validatorSetSize = 1; validatorSetSize <= 128; validatorSetSize *= 2) {
      for (int sizeOfCache = 1; sizeOfCache <= 128; sizeOfCache *= 2) {
        setUp(validatorSetSize, sizeOfCache);

        // 2 round robins
        final int viewsToTest = 2 * validatorSetSize;

        for (int view = 0; view < viewsToTest; view++) {
          var expectedNodeForView =
              validatorsInOrder.get(validatorSetSize - (view % validatorSetSize) - 1).getNode();
          assertThat(weightedRotatingLeaders.getProposer(View.of(view)))
              .isEqualTo(expectedNodeForView);
        }
      }
    }
  }

  @Test
  public void when_get_proposer_multiple_times__then_should_return_the_same_key() {
    for (int validatorSetSize = 1; validatorSetSize <= 128; validatorSetSize *= 2) {
      for (int sizeOfCache = 1; sizeOfCache <= 128; sizeOfCache *= 2) {
        setUp(validatorSetSize, sizeOfCache);

        // 2 * sizeOfCache so cache eviction occurs
        final int viewsToTest = 2 * sizeOfCache;

        BFTNode expectedNodeForView0 = weightedRotatingLeaders.getProposer(View.of(0));
        for (View view = View.of(1);
            view.compareTo(View.of(viewsToTest)) <= 0;
            view = view.next()) {
          weightedRotatingLeaders.getProposer(view);
        }
        assertThat(weightedRotatingLeaders.getProposer(View.of(0))).isEqualTo(expectedNodeForView0);
      }
    }
  }

  @Test
  public void when_get_proposer_skipping_views__then_should_return_same_result_as_in_order() {
    for (int validatorSetSize = 1; validatorSetSize <= 128; validatorSetSize *= 2) {
      for (int sizeOfCache = 1; sizeOfCache <= 128; sizeOfCache *= 2) {
        setUp(validatorSetSize, sizeOfCache);

        // 2 * sizeOfCache so cache eviction occurs
        final int viewsToTest = 2 * sizeOfCache;

        for (int view = 0; view < viewsToTest; view++) {
          weightedRotatingLeaders2.getProposer(View.of(view));
        }
        BFTNode node1 = weightedRotatingLeaders.getProposer(View.of(viewsToTest - 1));
        BFTNode node2 = weightedRotatingLeaders2.getProposer(View.of(viewsToTest - 1));
        assertThat(node1).isEqualTo(node2);
      }
    }
  }

  @Test
  public void
      when_validators_distributed_by_fibonacci__then_leaders_also_distributed_in_fibonacci() {
    // fibonacci sequence can quickly explode so keep sizes small
    final int validatorSetSize = 8;
    final int sizeOfCache = 4;
    final Supplier<IntStream> fibonacci =
        () ->
            Stream.iterate(new int[] {1, 1}, t -> new int[] {t[1], t[0] + t[1]})
                .mapToInt(t -> t[0])
                .limit(validatorSetSize);

    final int sumOfPower = fibonacci.get().sum();
    this.validatorsInOrder =
        fibonacci
            .get()
            .mapToObj(p -> BFTValidator.from(BFTNode.random(), UInt256.from(p)))
            .collect(ImmutableList.toImmutableList());

    BFTValidatorSet validatorSet = BFTValidatorSet.from(validatorsInOrder);
    this.weightedRotatingLeaders = new WeightedRotatingLeaders(validatorSet, sizeOfCache);

    Map<BFTNode, UInt256> proposerCounts =
        Stream.iterate(View.of(0), View::next)
            .limit(sumOfPower)
            .map(this.weightedRotatingLeaders::getProposer)
            .collect(groupingBy(p -> p, collectingAndThen(counting(), UInt256::from)));

    Map<BFTNode, UInt256> expected =
        validatorsInOrder.stream().collect(toMap(BFTValidator::getNode, BFTValidator::getPower));

    assertThat(proposerCounts).isEqualTo(expected);
  }
}
