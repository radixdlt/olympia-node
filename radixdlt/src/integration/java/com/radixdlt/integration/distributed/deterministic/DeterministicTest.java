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

package com.radixdlt.integration.distributed.deterministic;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.radixdlt.syncer.EpochChangeSender;
import com.radixdlt.consensus.SyncedExecutor;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.integration.distributed.deterministic.ControlledNetwork.ChannelId;
import com.radixdlt.integration.distributed.deterministic.ControlledNetwork.ControlledMessage;
import com.radixdlt.integration.distributed.deterministic.ControlledNetwork.ControlledSender;
import com.radixdlt.integration.distributed.deterministic.configuration.SingleEpochAlwaysSyncedExecutor;
import com.radixdlt.integration.distributed.deterministic.configuration.SingleEpochRandomlySyncedExecutor;
import com.radixdlt.syncer.SyncExecutor.CommittedStateSyncSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.utils.UInt256;

import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A deterministic test where each event that occurs in the network
 * is emitted and processed synchronously by the caller.
 */
public final class DeterministicTest {
	private static final String LOST_RESPONSIVENESS = "No messages available (Lost Responsiveness)";

	private final ImmutableList<BFTNode> bftNodes;
	private final LongFunction<BFTValidatorSet> validatorSetMapping;
	private final boolean timeoutEnabled;
	private final BiFunction<CommittedStateSyncSender, EpochChangeSender, SyncedExecutor> stateComputerFactory;

	private final ControlledNetwork network;

	private ImmutableList<ControlledNode> nodes;

	private DeterministicTest(
		ImmutableList<BFTNode> nodes,
		LongFunction<BFTValidatorSet> validatorSetMapping,
		boolean timeoutEnabled,
		BiFunction<CommittedStateSyncSender, EpochChangeSender, SyncedExecutor> stateComputerFactory
	) {
		this.bftNodes = Objects.requireNonNull(nodes);
		this.validatorSetMapping = Objects.requireNonNull(validatorSetMapping);
		this.timeoutEnabled = timeoutEnabled;
		this.stateComputerFactory = Objects.requireNonNull(stateComputerFactory);

		this.network = new ControlledNetwork();
	}

	public static class Builder {
		private ImmutableList<BFTNode> nodes = ImmutableList.of(BFTNode.create(ECKeyPair.generateNew().getPublicKey()));
		private boolean timeoutEnabled = true;
		private LongFunction<Stream<NodeIndexAndWeight>> epochToNodesMapper;
		BiFunction<CommittedStateSyncSender, EpochChangeSender, SyncedExecutor> stateComputerFactory =
			(committedSender, epochChangeSender) -> SingleEpochAlwaysSyncedExecutor.INSTANCE;

		private Builder() {
			// Nothing to do here
		}

		public Builder numNodes(int numNodes) {
			this.nodes = Stream.generate(ECKeyPair::generateNew)
				.limit(numNodes)
				.sorted(Comparator.<ECKeyPair, EUID>comparing(k -> k.getPublicKey().euid()).reversed())
				.map(kp -> BFTNode.create(kp.getPublicKey()))
				.collect(ImmutableList.toImmutableList());
			return this;
		}

		public Builder epochToNodeIndexesMapper(LongFunction<IntStream> epochToNodeMapper) {
			this.epochToNodesMapper = epoch -> equalWeight(epochToNodeMapper.apply(epoch));
			return this;
		}

		public Builder epochToNodesMapper(LongFunction<Stream<NodeIndexAndWeight>> epochToNodeMapper) {
			this.epochToNodesMapper = epochToNodeMapper;
			return this;
		}

		public Builder timeoutEnabled(boolean timeoutEnabled) {
			this.timeoutEnabled = timeoutEnabled;
			return this;
		}

		public Builder stateComputerFactory(
			BiFunction<CommittedStateSyncSender, EpochChangeSender, SyncedExecutor> stateComputerFactory
		) {
			this.stateComputerFactory = stateComputerFactory;
			return this;
		}

		public DeterministicTest build() {
			LongFunction<BFTValidatorSet> validatorSetMapping = epochToNodesMapper == null
				? epoch -> completeEqualWeightValidatorSet(this.nodes)
				: epoch -> partialMixedWeightValidatorSet(epoch, this.nodes, this.epochToNodesMapper);
			return new DeterministicTest(this.nodes, validatorSetMapping, this.timeoutEnabled, this.stateComputerFactory);
		}

		private static BFTValidatorSet completeEqualWeightValidatorSet(ImmutableList<BFTNode> nodes) {
			return BFTValidatorSet.from(
				nodes.stream()
					.map(node -> BFTValidator.from(node, UInt256.ONE))
			);
		}

		private static BFTValidatorSet partialMixedWeightValidatorSet(
			long epoch,
			ImmutableList<BFTNode> nodes,
			LongFunction<Stream<NodeIndexAndWeight>> mapper
		) {
			return BFTValidatorSet.from(
				mapper.apply(epoch)
					.map(niw -> BFTValidator.from(nodes.get(niw.index()), niw.weight()))
			);
		}

		private static Stream<NodeIndexAndWeight> equalWeight(IntStream indexes) {
			return indexes.mapToObj(i -> NodeIndexAndWeight.from(i, UInt256.ONE));
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Creates a new randomly synced BFT/SyncedStateComputer test
	 * @param numNodes number of nodes in the network
	 * @param random the randomizer
	 * @return a deterministic test
	 */
	public static DeterministicTest createSingleEpochRandomlySyncedTest(int numNodes, Random random) {
		return builder()
			.numNodes(numNodes)
			.timeoutEnabled(false)
			.stateComputerFactory((committedSender, epochSender) -> new SingleEpochRandomlySyncedExecutor(random, committedSender))
			.build();
	}

	/**
	 * Creates a new "always synced BFT" Deterministic test solely on the bft layer,
	 *
	 * @param numNodes number of nodes in the network
	 * @return a deterministic test
	 */
	public static DeterministicTest createSingleEpochAlwaysSyncedTest(int numNodes) {
		return builder()
			.numNodes(numNodes)
			.timeoutEnabled(false)
			.stateComputerFactory((committedSender, epochSender) -> SingleEpochAlwaysSyncedExecutor.INSTANCE)
			.build();
	}

	/**
	 * Creates a new "always synced BFT" Deterministic test solely on the BFT layer,
	 *
	 * @param numNodes number of nodes in the network
	 * @param weight a mapping from node index to node weight
	 * @return a deterministic test
	 */
	public static DeterministicTest createSingleEpochAlwaysSyncedTest(int numNodes, LongFunction<Stream<NodeIndexAndWeight>> weight) {
		return builder()
			.numNodes(numNodes)
			.timeoutEnabled(false)
			.stateComputerFactory((committedSender, epochSender) -> SingleEpochAlwaysSyncedExecutor.INSTANCE)
			.epochToNodesMapper(weight)
			.build();
	}

	/**
	 * Creates a new "always synced BFT with timeouts" Deterministic test solely on the bft layer,
	 *
	 * @param numNodes number of nodes in the network
	 * @return a deterministic test
	 */
	public static DeterministicTest createSingleEpochAlwaysSyncedWithTimeoutsTest(int numNodes) {
		return builder()
			.numNodes(numNodes)
			.timeoutEnabled(true)
			.stateComputerFactory((committedSender, epochSender) -> SingleEpochAlwaysSyncedExecutor.INSTANCE)
			.build();
	}

	public void start() {
		BFTValidatorSet initialValidatorSet = this.validatorSetMapping.apply(1L);
		this.nodes = Streams.mapWithIndex(this.bftNodes.stream().map(BFTNode::getKey), (key, index) -> {
				ControlledSender sender = network.createSender(bftNodes.get((int) index));
				return new ControlledNode(
					key,
					this.network,
					sender,
					vset -> new WeightedRotatingLeaders(vset, Comparator.comparing(v -> v.getNode().getKey().euid()), 5),
					initialValidatorSet,
					this.timeoutEnabled,
					this.stateComputerFactory.apply(sender, sender)
				);
			})
			.collect(ImmutableList.toImmutableList());
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

	public void dumpMessages(PrintStream out) {
		network.dumpMessages(out, bftNodes::indexOf);
	}
}
