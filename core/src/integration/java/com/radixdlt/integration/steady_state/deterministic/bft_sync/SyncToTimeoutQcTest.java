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

package com.radixdlt.integration.steady_state.deterministic.bft_sync;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.monitoring.SystemCounters.CounterType;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.harness.deterministic.DeterministicTest;
import java.util.Random;
import org.junit.Test;

/**
 * If quorum is formed on a timeout (timeout certificate), and there's a node that's a single view
 * behind (i.e. it didn't participate in forming of TC). Then it should be able to sync up (move to
 * next view) as soon as it receives a proposal (with a TC). BFTSync should then immediately switch
 * to next view without any additional sync requests. The setup is as follows: 1. there are 4 nodes
 * 2. proposal is sent by the leader (0) but only received by 2 nodes (including the leader): 0 and
 * 1 3. two nodes vote on a proposal (0 and 1) 4. two nodes vote on an empty timeout vertex and
 * broadcast the vote (2 and 3) 5. nodes 0 and 1 resend (broadcast) their vote with a timeout flag
 * 6. node 0 doesn't receive any of the above votes 7. nodes 1, 2 and 3 can form a valid TC out of
 * the votes they received, and they switch to the next view 8. next leader (node 1) sends out a
 * proposal 9. proposal (with a valid TC) is received by node 0 (which is still on previous view)
 * 10. node 0 is able to move to the next view just by processing the proposal's TC (no additional
 * sync requests) Expected result: node 0 is at view 2 and no sync requests have been sent
 */
public class SyncToTimeoutQcTest {

  private static final int NUM_NODES = 4;

  private final Random random = new Random(123456);

  @Test
  public void sync_to_timeout_qc_test() {
    final DeterministicTest test =
        DeterministicTest.builder()
            .numNodes(NUM_NODES)
            .messageSelector(MessageSelector.randomSelector(random))
            .messageMutator(dropProposalsToNodes(ImmutableSet.of(2, 3)).andThen(dropVotesToNode(0)))
            .buildWithEpochs(View.of(10))
            .runUntil(DeterministicTest.viewUpdateOnNode(View.of(2), 0));

    for (int nodeIndex = 0; nodeIndex < NUM_NODES; ++nodeIndex) {
      final var counters = test.getSystemCounters(nodeIndex);
      // no bft sync requests were needed
      assertEquals(0, counters.get(CounterType.BFT_SYNC_REQUESTS_SENT));
    }
  }

  private static MessageMutator dropVotesToNode(int nodeIndex) {
    return (message, queue) -> {
      final var msg = message.message();
      if (msg instanceof Vote) {
        return message.channelId().receiverIndex() == nodeIndex;
      }
      return false;
    };
  }

  private static MessageMutator dropProposalsToNodes(ImmutableSet<Integer> nodesIndices) {
    return (message, queue) -> {
      final var msg = message.message();
      if (msg instanceof Proposal) {
        return nodesIndices.contains(message.channelId().receiverIndex());
      }
      return false;
    };
  }
}
