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

package com.radixdlt.integration.steady_state.deterministic.consensus;

import static org.assertj.core.api.Assertions.*;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.Epoched;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.monitoring.SystemCounters.CounterType;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.harness.deterministic.DeterministicTest;
import com.radixdlt.harness.deterministic.configuration.EpochNodeWeightMapping;
import com.radixdlt.utils.UInt256;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.assertj.core.api.Condition;
import org.junit.Test;

public class ProposerLoadBalancedTest {

  private ImmutableList<Long> run(int numNodes, long numViews, EpochNodeWeightMapping mapping) {

    DeterministicTest test =
        DeterministicTest.builder()
            .numNodes(numNodes)
            .messageSelector(MessageSelector.firstSelector())
            .messageMutator(mutator())
            .epochNodeWeightMapping(mapping)
            .buildWithoutEpochs()
            .runUntil(DeterministicTest.hasReachedView(View.of(numViews)));

    return IntStream.range(0, numNodes)
        .mapToObj(test::getSystemCounters)
        .map(counters -> counters.get(CounterType.BFT_PACEMAKER_PROPOSALS_SENT))
        .collect(ImmutableList.toImmutableList());
  }

  private MessageMutator mutator() {
    return (message, queue) -> {
      Object msg = message.message();
      if (msg instanceof ScheduledLocalTimeout
          || Epoched.isInstance(msg, ScheduledLocalTimeout.class)) {
        return true;
      }

      // Process others in submission order
      queue.add(message.withArrivalTime(0L));
      return true;
    };
  }

  @Test
  public void when_run_2_nodes_with_very_different_weights__then_proposals_should_match() {
    final int numNodes = 2;
    final long proposalChunk = 20_000L; // Actually proposalChunk + 1 proposals run
    ImmutableList<Long> proposals =
        this.run(
            numNodes,
            proposalChunk + 1,
            EpochNodeWeightMapping.repeatingSequence(numNodes, 1, proposalChunk));
    assertThat(proposals).containsExactly(1L, proposalChunk);
  }

  @Test
  public void when_run_3_nodes_with_equal_weight__then_proposals_should_be_equal() {
    final int numNodes = 3;
    final long proposalsPerNode = 50_000L;
    ImmutableList<Long> proposals =
        this.run(
            numNodes, numNodes * proposalsPerNode, EpochNodeWeightMapping.constant(numNodes, 1L));
    assertThat(proposals)
        .hasSize(numNodes)
        .areAtLeast(
            numNodes - 1,
            new Condition<>(l -> l == proposalsPerNode, "has as many proposals as views"))
        // the last view in the epoch doesn't have a proposal
        .areAtMost(1, new Condition<>(l -> l == proposalsPerNode - 1, "has one less proposal"));
  }

  @Test
  public void when_run_100_nodes_with_equal_weight__then_proposals_should_be_equal() {
    final int numNodes = 100;
    final long proposalsPerNode = 10L;
    ImmutableList<Long> proposals =
        this.run(numNodes, numNodes * proposalsPerNode, EpochNodeWeightMapping.constant(100, 1L));
    assertThat(proposals)
        .hasSize(numNodes)
        .areAtLeast(
            numNodes - 1,
            new Condition<>(l -> l == proposalsPerNode, "has as many proposals as views"))
        // the last view in the epoch doesn't have a proposal
        .areAtMost(1, new Condition<>(l -> l == proposalsPerNode - 1, "has one less proposal"));
  }

  @Test
  public void when_run_3_nodes_with_linear_weights__then_proposals_should_match() {
    final long proposalChunk = 20_000L; // Actually 3! * proposalChunk proposals run
    List<Long> proposals =
        this.run(
            3, 1 * 2 * 3 * proposalChunk, EpochNodeWeightMapping.repeatingSequence(3, 1, 2, 3));
    assertThat(proposals).containsExactly(proposalChunk, 2 * proposalChunk, 3 * proposalChunk);
  }

  @Test
  public void when_run_100_nodes_with_two_different_weights__then_proposals_should_match() {
    final int numNodes = 100;
    // Nodes 0..49 have weight 1; nodes 50..99 have weight 2
    final long proposalChunk = 10L; // Actually 150 * proposalChunk proposals run
    ImmutableList<Long> proposals =
        this.run(
            100,
            150 * proposalChunk,
            EpochNodeWeightMapping.computed(
                numNodes, index -> UInt256.from(index / 50 + 1)) // Weights 1, 1, ..., 2, 2
            );

    assertThat(proposals.subList(0, 50))
        .areAtLeast(49, new Condition<>(l -> l == proposalChunk, "has as many proposals as views"))
        // the last view in the epoch doesn't have a proposal
        .areAtMost(1, new Condition<>(l -> l == proposalChunk - 1, "has one less proposal"));

    assertThat(proposals.subList(50, 100)).allMatch(Long.valueOf(2 * proposalChunk)::equals);
  }

  @Test
  public void when_run_3_nodes_with_large_lcm_weighting__then_proposals_should_be_proportional() {
    final int numNodes = 3;
    final long numProposals = 100_000L;
    ImmutableList<UInt256> weights =
        ImmutableList.of(
            // Some large primes with product/LCM > 2^64 but < 2^256
            UInt256.from("941083981"), UInt256.from("961748927"), UInt256.from("982451653"));
    UInt256 sum = weights.stream().reduce(UInt256.ZERO, UInt256::add);
    UInt256 numViews256 = UInt256.from(numProposals);
    long[] values =
        weights.stream()
            .map(w -> w.multiply(numViews256).divide(sum))
            .mapToLong(v -> v.getLow().getLow())
            .toArray();
    ImmutableList<Long> proposals =
        this.run(numNodes, numProposals, EpochNodeWeightMapping.computed(numNodes, weights::get));
    // Correct number of total proposals
    assertThat(proposals.stream().mapToLong(Long::longValue).sum()).isEqualTo(numProposals);
    // Same as calculated value, +/- 1 (rounding and ordering)
    for (int i = 0; i < values.length; ++i) {
      assertThat(proposals.get(i).longValue()).isBetween(values[i] - 1, values[i] + 1);
    }
  }

  @Test
  public void when_run_100_nodes_with_very_large_period__then_proposals_should_be_proportional() {
    final int numNodes = 100;
    final long numProposals = 1_000L;
    ImmutableList<UInt256> weights =
        generatePrimes(100).mapToObj(UInt256::from).collect(ImmutableList.toImmutableList());
    UInt256 sum = weights.stream().reduce(UInt256.ZERO, UInt256::add);
    UInt256 numViews256 = UInt256.from(numProposals);
    long[] values =
        weights.stream()
            .map(w -> w.multiply(numViews256).divide(sum))
            .mapToLong(v -> v.getLow().getLow())
            .toArray();
    ImmutableList<Long> proposals =
        this.run(numNodes, numProposals, EpochNodeWeightMapping.computed(numNodes, weights::get));
    // Correct number of total proposals
    assertThat(proposals.stream().mapToLong(Long::longValue).sum()).isEqualTo(numProposals);
    // Same as calculated value, +/- 1 (rounding and ordering)
    for (int i = 0; i < values.length; ++i) {
      assertThat(proposals.get(i).longValue()).isBetween(values[i] - 1, values[i] + 1);
    }
  }

  private static LongStream generatePrimes(int n) {
    // Just FYI, doesn't include 2.  You don't need it.
    return LongStream.iterate(3L, m -> m + 2).filter(ProposerLoadBalancedTest::isPrime).limit(n);
  }

  private static boolean isPrime(long number) {
    return LongStream.rangeClosed(1L, (long) Math.sqrt(number) / 2L)
        .map(n -> n * 2 + 1)
        .noneMatch(n -> number % n == 0);
  }
}
