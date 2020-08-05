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

package com.radixdlt.consensus.deterministic;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.radixdlt.EpochChangeSender;
import com.radixdlt.consensus.SyncedStateComputer;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.deterministic.ControlledNetwork.ChannelId;
import com.radixdlt.consensus.deterministic.ControlledNetwork.ControlledMessage;
import com.radixdlt.consensus.deterministic.ControlledNetwork.ControlledSender;
import com.radixdlt.consensus.deterministic.ControlledNode.SyncAndTimeout;
import com.radixdlt.consensus.deterministic.configuration.SingleEpochAlwaysSyncedStateComputer;
import com.radixdlt.consensus.deterministic.configuration.SingleEpochFailOnSyncStateComputer;
import com.radixdlt.consensus.deterministic.configuration.SingleEpochRandomlySyncedStateComputer;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.consensus.sync.SyncedRadixEngine.CommittedStateSyncSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.utils.UInt256;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A deterministic test where each event that occurs in the network
 * is emitted and processed synchronously by the caller.
 */
public final class DeterministicTest {
	private static final String LOST_RESPONSIVENESS = "No messages available (Lost Responsiveness)";
	private final ImmutableList<ControlledNode> nodes;
	private final ImmutableList<BFTNode> bftNodes;
	private final ControlledNetwork network;

	private DeterministicTest(
		int numNodes,
		SyncAndTimeout syncAndTimeout,
		BiFunction<CommittedStateSyncSender, EpochChangeSender, SyncedStateComputer<CommittedAtom>> stateComputerSupplier,
		NodeWeighting weight
	) {
		ImmutableList<ECKeyPair> keys = Stream.generate(ECKeyPair::generateNew)
			.limit(numNodes)
			.sorted(Comparator.<ECKeyPair, EUID>comparing(k -> k.getPublicKey().euid()).reversed())
			.collect(ImmutableList.toImmutableList());
		this.bftNodes = keys.stream()
			.map(ECKeyPair::getPublicKey)
			.map(BFTNode::create)
			.collect(ImmutableList.toImmutableList());
		this.network = new ControlledNetwork();
		BFTValidatorSet initialValidatorSet = BFTValidatorSet.from(
			Streams.mapWithIndex(
				bftNodes.stream(),
				(pk, index) -> BFTValidator.from(pk, weight.forNode((int) index))
			).collect(Collectors.toList())
		);

		this.nodes = Streams.mapWithIndex(keys.stream(),
			(key, index) -> {
				ControlledSender sender = network.createSender(bftNodes.get((int) index));
				return new ControlledNode(
					key,
					sender,
					vset -> new WeightedRotatingLeaders(vset, Comparator.comparing(v -> v.getNode().getKey().euid()), 5),
					initialValidatorSet,
					syncAndTimeout,
					stateComputerSupplier.apply(sender, sender)
				);
			})
			.collect(ImmutableList.toImmutableList());
	}

	/**
	 * Creates a new randomly synced BFT/SyncedStateComputer test
	 * @param numNodes number of nodes in the network
	 * @param random the randomizer
	 * @return a deterministic test
	 */
	public static DeterministicTest createSingleEpochRandomlySyncedTest(int numNodes, Random random) {
		return new DeterministicTest(
			numNodes,
			SyncAndTimeout.SYNC,
			(committedSender, epochSender) -> new SingleEpochRandomlySyncedStateComputer(random, committedSender),
			NodeWeighting.constant(UInt256.ONE)
		);
	}

	/**
	 * Creates a new "always synced BFT" Deterministic test solely on the bft layer,
	 *
	 * @param numNodes number of nodes in the network
	 * @return a deterministic test
	 */
	public static DeterministicTest createSingleEpochAlwaysSyncedTest(int numNodes) {
		return new DeterministicTest(
			numNodes,
			SyncAndTimeout.SYNC,
			(committedSender, epochChangeSender) -> SingleEpochAlwaysSyncedStateComputer.INSTANCE,
			NodeWeighting.constant(UInt256.ONE)
		);
	}

	/**
	 * Creates a new "always synced BFT" Deterministic test solely on the BFT layer,
	 *
	 * @param numNodes number of nodes in the network
	 * @param weight a mapping from node index to node weight
	 * @return a deterministic test
	 */
	public static DeterministicTest createSingleEpochAlwaysSyncedTest(int numNodes, NodeWeighting weight) {
		return new DeterministicTest(
			numNodes,
			SyncAndTimeout.SYNC,
			(committedSender, epochChangeSender) -> SingleEpochAlwaysSyncedStateComputer.INSTANCE,
			weight
		);
	}

	/**
	 * Creates a new "always synced BFT with timeouts" Deterministic test solely on the bft layer,
	 *
	 * @param numNodes number of nodes in the network
	 * @return a deterministic test
	 */
	public static DeterministicTest createSingleEpochAlwaysSyncedWithTimeoutsTest(int numNodes) {
		return new DeterministicTest(
			numNodes,
			SyncAndTimeout.SYNC_AND_TIMEOUT,
			(committedSender, epochChangeSender) -> SingleEpochAlwaysSyncedStateComputer.INSTANCE,
			NodeWeighting.constant(UInt256.ONE)
		);
	}

	/**
	 * Creates a new "non syncing BFT" Deterministic test solely on the bft layer,
	 * "non syncing BFT" implying that the configuration of the network should never
	 * require a vertex sync nor a state computer sync
	 *
	 * @param numNodes number of nodes in the network
	 * @return a deterministic test
	 */
	public static DeterministicTest createSingleEpochFailOnSyncTest(int numNodes) {
		return new DeterministicTest(
			numNodes,
			SyncAndTimeout.NONE,
			(committedSender, epochChangeSender) -> SingleEpochFailOnSyncStateComputer.INSTANCE,
			NodeWeighting.constant(UInt256.ONE)
		);
	}

	public void start() {
		nodes.forEach(ControlledNode::start);
	}

	public SystemCounters getSystemCounters(int nodeIndex) {
		return nodes.get(nodeIndex).getSystemCounters();
	}

	public void processNextMsg(int toIndex, int fromIndex, Class<?> expectedClass) {
		processNextMsg(toIndex, fromIndex, expectedClass, Function.identity());
	}

	public <T, U> void processNextMsg(int toIndex, int fromIndex, Class<T> expectedClass, Function<T, U> mutator) {
		ChannelId channelId = new ChannelId(bftNodes.get(fromIndex), bftNodes.get(toIndex));
		Object msg = network.popNextMessage(channelId);
		assertThat(msg).isInstanceOf(expectedClass);
		U msgToUse = mutator.apply(expectedClass.cast(msg));
		if (msgToUse != null) {
			nodes.get(toIndex).processNext(msgToUse);
		}
	}

	// TODO: This collection of interfaces will need a rethink once we have
	// more complicated adversaries that need access to the whole message queue.

	public void processNextMsg(Random random) {
		processNextMsgWithReceiver(random, (c, m) -> true);
	}

	public void processNextMsg(Random random, Predicate<Object> filter) {
		processNextMsgWithReceiver(random, (receiverIndex, msg) -> filter.test(msg));
	}

	public void processNextMsgWithReceiver(Random random, BiPredicate<Integer, Object> filter) {
		List<ControlledMessage> possibleMsgs = network.peekNextMessages();
		if (possibleMsgs.isEmpty()) {
			throw new IllegalStateException(LOST_RESPONSIVENESS);
		}

		int nextIndex =  random.nextInt(possibleMsgs.size());
		ChannelId channelId = possibleMsgs.get(nextIndex).getChannelId();
		Object msg = network.popNextMessage(channelId);
		int receiverIndex = bftNodes.indexOf(channelId.getReceiver());
		if (filter.test(receiverIndex, msg)) {
			nodes.get(receiverIndex).processNext(msg);
		}
	}

	public void processNextMsgWithSenderAndReceiver(Random random, TriPredicate<Integer, Integer, Object> filter) {
		List<ControlledMessage> possibleMsgs = network.peekNextMessages();
		if (possibleMsgs.isEmpty()) {
			throw new IllegalStateException(LOST_RESPONSIVENESS);
		}

		int nextIndex =  random.nextInt(possibleMsgs.size());
		ChannelId channelId = possibleMsgs.get(nextIndex).getChannelId();
		Object msg = network.popNextMessage(channelId);
		int receiverIndex = bftNodes.indexOf(channelId.getReceiver());
		int senderIndex = bftNodes.indexOf(channelId.getSender());
		if (filter.test(senderIndex, receiverIndex, msg)) {
			nodes.get(receiverIndex).processNext(msg);
		}
	}
}
