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

import static org.assertj.core.api.Assertions.assertThat;

import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.monitoring.SystemCounters.CounterType;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.harness.deterministic.DeterministicTest;
import java.util.Random;
import org.junit.Test;

/**
 * When original votes (to next view leader, non timed out) are dropped, nodes should be able to
 * resend those votes to each other (with timeout) and form the quorum themselves. As a result,
 * there should be no timeout (non-QC) quorums and no indirect parents.
 */
public class QuorumWithoutALeaderWithTimeoutsTest {

  private final Random random = new Random(123456);

  private void run(int numNodes, long numViews) {
    final DeterministicTest test =
        DeterministicTest.builder()
            .numNodes(numNodes)
            .messageSelector(MessageSelector.randomSelector(random))
            .messageMutator(dropAllNonTimeoutVotes())
            .buildWithoutEpochs()
            .runUntil(DeterministicTest.hasReachedView(View.of(numViews)));

    for (int nodeIndex = 0; nodeIndex < numNodes; ++nodeIndex) {
      final SystemCounters counters = test.getSystemCounters(nodeIndex);
      final long numberOfIndirectParents =
          counters.get(CounterType.BFT_VERTEX_STORE_INDIRECT_PARENTS);
      final long totalNumberOfTimeouts = counters.get(CounterType.BFT_PACEMAKER_TIMEOUTS_SENT);
      final long totalNumberOfTimeoutQuorums = counters.get(CounterType.BFT_TIMEOUT_QUORUMS);
      final long totalNumberOfVoteQuorums = counters.get(CounterType.BFT_VOTE_QUORUMS);
      assertThat(totalNumberOfTimeoutQuorums).isEqualTo(0); // no TCs
      assertThat(numberOfIndirectParents).isEqualTo(0); // no indirect parents
      assertThat(totalNumberOfTimeouts).isEqualTo(numViews - 1); // a timeout for each view
      assertThat(totalNumberOfVoteQuorums)
          .isBetween(numViews - 2, numViews); // quorum count matches views
    }
  }

  private static MessageMutator dropAllNonTimeoutVotes() {
    return (message, queue) -> {
      final Object msg = message.message();
      if (msg instanceof Vote) {
        final Vote vote = (Vote) msg;
        return vote.getTimeoutSignature().isEmpty();
      }
      return false;
    };
  }

  @Test
  public void when_run_3_correct_nodes_for_50k_views__then_bft_should_be_responsive() {
    this.run(3, 50_000);
  }

  @Test
  public void when_run_10_correct_nodes_with_for_2k_views__then_bft_should_be_responsive() {
    this.run(10, 2000);
  }

  @Test
  public void when_run_100_correct_nodes_with_for_50_views__then_bft_should_be_responsive() {
    this.run(100, 50);
  }
}
