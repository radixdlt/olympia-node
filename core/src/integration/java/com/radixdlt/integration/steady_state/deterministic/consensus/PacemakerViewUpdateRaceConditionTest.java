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

import com.google.common.hash.HashCode;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.harness.deterministic.DeterministicTest;
import com.radixdlt.utils.KeyComparator;
import io.reactivex.rxjava3.schedulers.Timed;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;
import org.junit.Test;

/**
 * This test checks that when race condition is introduced (by delaying ViewUpdate and
 * BFTInsertUpdate messages) then the Pacemaker can form a valid timeout vote for an empty proposal.
 * Specifically, it checks whether the vertices it inserts use a correct parent.
 */
public class PacemakerViewUpdateRaceConditionTest {

  private static final Random random = new Random(123456);

  private static final int numNodes = 4;
  private static final int nodeUnderTestIndex = 2; // leader for view 2
  private static final long pacemakerTimeout = 1000L;
  private static final long additionalMessageDelay = pacemakerTimeout + 1000L;

  @Test
  public void test_pacemaker_view_update_race_condition() {
    final DeterministicTest test =
        DeterministicTest.builder()
            .numNodes(numNodes)
            .messageSelector(MessageSelector.randomSelector(random))
            .messageMutator(messUpMessagesForNodeUnderTest())
            .pacemakerTimeout(pacemakerTimeout)
            .overrideWithIncorrectModule(
                new AbstractModule() {
                  @ProvidesIntoSet
                  @ProcessOnDispatch
                  private EventProcessor<BFTInsertUpdate> bftInsertUpdateProcessor() {
                    final Map<HashCode, PreparedVertex> insertedVertices = new HashMap<>();
                    return bftInsertUpdate -> {
                      final PreparedVertex inserted = bftInsertUpdate.getInserted();
                      insertedVertices.putIfAbsent(inserted.getId(), inserted);
                      final Optional<PreparedVertex> maybeParent =
                          Optional.ofNullable(insertedVertices.get(inserted.getParentId()));

                      maybeParent.ifPresent(
                          parent -> {
                            if (parent.getView().equals(inserted.getView())) {
                              throw new IllegalStateException(
                                  "Vertex can't have the same view as its parent.");
                            }
                          });
                    };
                  }

                  @Provides
                  public ProposerElection proposerElection(BFTValidatorSet validatorSet) {
                    final var sortedValidators =
                        validatorSet.getValidators().stream()
                            .map(BFTValidator::getNode)
                            .sorted(
                                Comparator.comparing(
                                    BFTNode::getKey, KeyComparator.instance().reversed()))
                            .toList();
                    return view ->
                        sortedValidators.get(((int) view.number() - 1) % sortedValidators.size());
                  }
                })
            .buildWithoutEpochs()
            .runUntil(nodeUnderTestReachesView(View.of(3)));

    final var counters = test.getSystemCounters(nodeUnderTestIndex);
    assertThat(counters.get(SystemCounters.CounterType.BFT_VOTE_QUORUMS))
        .isEqualTo(2); // ensure that quorum was formed
    assertThat(counters.get(SystemCounters.CounterType.BFT_PACEMAKER_TIMEOUTS_SENT))
        .isEqualTo(2); // ensure that timeouts were processed
  }

  private static Predicate<Timed<ControlledMessage>> nodeUnderTestReachesView(View view) {
    return timedMsg -> {
      final ControlledMessage message = timedMsg.value();
      if (!(message.message() instanceof ViewUpdate)) {
        return false;
      }
      final ViewUpdate p = (ViewUpdate) message.message();
      return message.channelId().receiverIndex() == nodeUnderTestIndex
          && p.getCurrentView().gte(view);
    };
  }

  private static MessageMutator messUpMessagesForNodeUnderTest() {
    return (message, queue) -> {
      // we only mess up messages for the test node
      if (message.channelId().receiverIndex() != nodeUnderTestIndex) {
        return false;
      }

      // the unlucky node doesn't receive a Proposal and its next ViewUpdate and BFTInsertUpdate
      // messages are delayed
      // Proposal is dropped so that the node creates an empty timeout vote, and not a timeout of a
      // previous vote
      final Object msg = message.message();
      if (msg instanceof ViewUpdate && ((ViewUpdate) msg).getCurrentView().equals(View.of(2))) {
        queue.add(message.withAdditionalDelay(additionalMessageDelay));
        return true;
      } else if (msg instanceof BFTInsertUpdate
          && ((BFTInsertUpdate) msg).getInserted().getView().equals(View.of(1))) {
        queue.add(message.withAdditionalDelay(additionalMessageDelay));
        return true;
      } else {
        return msg instanceof Proposal && ((Proposal) msg).getView().equals(View.of(1));
      }
    };
  }
}
