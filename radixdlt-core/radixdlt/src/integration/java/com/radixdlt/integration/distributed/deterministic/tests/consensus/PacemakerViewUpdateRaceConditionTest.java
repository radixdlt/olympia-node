/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.integration.distributed.deterministic.tests.consensus;

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
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.integration.distributed.deterministic.DeterministicTest;
import io.reactivex.rxjava3.schedulers.Timed;
import org.junit.Test;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test checks that when race condition is introduced (by delaying ViewUpdate and BFTInsertUpdate messages) then
 * the Pacemaker can form a valid timeout vote for an empty proposal.
 * Specifically, it checks whether the vertices it inserts use a correct parent.
 */
public class PacemakerViewUpdateRaceConditionTest {

	private static final Random random = new Random(123456);

	private static final int numNodes = 4;
	private static final int nodeUnderTestIndex = 1; // leader for view 2
	private static final long pacemakerTimeout = 1000L;
	private static final long additionalMessageDelay = pacemakerTimeout + 1000L;

	@Test
	public void test_pacemaker_view_update_race_condition() {
		final DeterministicTest test = DeterministicTest.builder()
			.numNodes(numNodes)
			.messageSelector(MessageSelector.randomSelector(random))
			.messageMutator(messUpMessagesForNodeUnderTest())
			.pacemakerTimeout(pacemakerTimeout)
			.overrideWithIncorrectModule(new AbstractModule() {
				@ProvidesIntoSet
				@ProcessOnDispatch
				private EventProcessor<BFTInsertUpdate> bftInsertUpdateProcessor() {
					final Map<HashCode, PreparedVertex> insertedVertices = new HashMap<>();
					return bftInsertUpdate -> {
						final PreparedVertex inserted = bftInsertUpdate.getInserted();
						insertedVertices.putIfAbsent(inserted.getId(), inserted);
						final Optional<PreparedVertex> maybeParent =
							Optional.ofNullable(insertedVertices.get(inserted.getParentId()));

						maybeParent.ifPresent(parent -> {
							if (parent.getView().equals(inserted.getView())) {
								throw new IllegalStateException("Vertex can't have the same view as its parent.");
							}
						});
					};
				}

				@Provides
				public ProposerElection proposerElection(BFTValidatorSet validatorSet) {
					final List<BFTNode> sortedValidators =
						validatorSet.getValidators().stream()
							.map(BFTValidator::getNode)
							.sorted(Comparator.<BFTNode, EUID>comparing(n -> n.getKey().euid()).reversed())
							.collect(Collectors.toList());
					return view -> sortedValidators.get(((int) view.number() - 1) % sortedValidators.size());
				}
			})
			.buildWithoutEpochs()
			.runUntil(nodeUnderTestReachesView(View.of(3)));

		final var counters = test.getSystemCounters(nodeUnderTestIndex);
		assertThat(counters.get(SystemCounters.CounterType.BFT_VOTE_QUORUMS)).isEqualTo(2); // ensure that quorum was formed
		assertThat(counters.get(SystemCounters.CounterType.BFT_TIMEOUT)).isEqualTo(2); // ensure that timeouts were processed
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

			// the unlucky node doesn't receive a Proposal and its next ViewUpdate and BFTInsertUpdate messages are delayed
			// Proposal is dropped so that the node creates an empty timeout vote, and not a timeout of a previous vote
			final Object msg = message.message();
			if (msg instanceof ViewUpdate
					&& ((ViewUpdate) msg).getCurrentView().equals(View.of(2))) {
				queue.add(message.withAdditionalDelay(additionalMessageDelay));
				return true;
			} else if (msg instanceof BFTInsertUpdate
					&& ((BFTInsertUpdate) msg).getInserted().getView().equals(View.of(1))) {
				queue.add(message.withAdditionalDelay(additionalMessageDelay));
				return true;
			} else {
				return msg instanceof Proposal
					&& ((Proposal) msg).getView().equals(View.of(1));
			}
		};
	}
}
