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

package com.radixdlt.integration.steady_state.deterministic.consensus_ledger_epochs;

import static com.radixdlt.environment.deterministic.network.MessageSelector.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.epoch.Epoched;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.monitoring.SystemCounters.CounterType;
import com.radixdlt.environment.deterministic.network.ChannelId;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.harness.deterministic.DeterministicTest;
import java.util.LinkedList;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Test;

public class MovingWindowValidatorsTest {

  private static LongFunction<IntStream> windowedEpochToNodesMapper(
      int windowSize, int totalValidatorCount) {
    // Epoch starts at 1, and we want base 0, so subtract 1
    return epoch ->
        IntStream.range(0, windowSize)
            .map(index -> (int) (epoch - 1 + index) % totalValidatorCount);
  }

  private void run(int numNodes, int windowSize, long maxEpoch, View highView) {
    DeterministicTest bftTest =
        DeterministicTest.builder()
            .numNodes(numNodes)
            .messageMutator(mutator())
            .messageSelector(firstSelector())
            .epochNodeIndexesMapping(windowedEpochToNodesMapper(windowSize, numNodes))
            .buildWithEpochs(highView)
            .runUntil(DeterministicTest.hasReachedEpochView(EpochView.of(maxEpoch, highView)));

    LinkedList<SystemCounters> testCounters = systemCounters(bftTest);
    assertThat(testCounters)
        .extracting(sc -> sc.get(CounterType.BFT_VERTEX_STORE_INDIRECT_PARENTS))
        .containsOnly(0L);
    assertThat(testCounters)
        .extracting(sc -> sc.get(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT))
        .containsOnly(0L);

    long maxCount = maxProcessedFor(numNodes, windowSize, maxEpoch, highView.number());

    assertThat(testCounters)
        .extracting(sc -> sc.get(CounterType.BFT_COMMITTED_VERTICES))
        .allMatch(between(maxCount - maxEpoch, maxCount));
  }

  private MessageMutator mutator() {
    return (message, queue) -> {
      if (Epoched.isInstance(message.message(), ScheduledLocalTimeout.class)) {
        // Discard
        return true;
      }
      // Process others in arrival order, local first.
      // Need to make sure EpochsLedgerUpdate is processed before consensus messages for the new
      // epoch
      if (nonLocalMessage(message)) {
        queue.add(message.withArrivalTime(0));
      } else {
        queue.addBefore(message.withArrivalTime(0), this::nonLocalMessage);
      }
      return true;
    };
  }

  private boolean nonLocalMessage(ControlledMessage msg) {
    ChannelId channelId = msg.channelId();
    return channelId.senderIndex() != channelId.receiverIndex();
  }

  @Test
  public void
      given_correct_1_node_bft_with_4_total_nodes_with_changing_epochs_per_100_views__then_should_pass_bft_and_postconditions() {
    run(4, 1, 100L, View.of(100));
  }

  @Test
  public void
      given_correct_3_node_bft_with_4_total_nodes_with_changing_epochs_per_100_views__then_should_pass_bft_and_postconditions() {
    run(4, 3, 120L, View.of(100));
  }

  @Test
  public void
      given_correct_25_node_bft_with_50_total_nodes_with_changing_epochs_per_100_views__then_should_pass_bft_and_postconditions() {
    run(50, 25, 100L, View.of(100));
  }

  @Test
  public void
      given_correct_25_node_bft_with_100_total_nodes_with_changing_epochs_per_1_view__then_should_pass_bft_and_postconditions() {
    run(100, 25, 100L, View.of(100));
  }

  private static long maxProcessedFor(
      int numNodes, int numValidators, long epochs, long epochHighView) {
    return epochHighView * epochs * numValidators / numNodes;
  }

  private static LinkedList<SystemCounters> systemCounters(DeterministicTest bftTest) {
    return IntStream.range(0, bftTest.numNodes())
        .mapToObj(bftTest::getSystemCounters)
        .collect(Collectors.toCollection(LinkedList::new));
  }

  private static Predicate<Long> between(long lower, long upper) {
    return value -> value >= lower && value <= upper;
  }
}
