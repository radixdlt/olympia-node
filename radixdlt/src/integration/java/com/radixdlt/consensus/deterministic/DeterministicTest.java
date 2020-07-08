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
import com.radixdlt.consensus.deterministic.ControlledNetwork.ChannelId;
import com.radixdlt.consensus.deterministic.ControlledNetwork.ControlledMessage;
import com.radixdlt.consensus.deterministic.ControlledNetwork.ControlledSender;
import com.radixdlt.consensus.deterministic.configuration.SingleEpochAlwaysSyncedStateComputer;
import com.radixdlt.consensus.deterministic.configuration.SingleEpochFailOnSyncStateComputer;
import com.radixdlt.consensus.deterministic.configuration.SingleEpochRandomlySyncedStateComputer;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.consensus.sync.SyncedRadixEngine.CommittedStateSyncSender;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.utils.UInt256;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A deterministic test where each event that occurs in the network
 * is emitted and processed synchronously by the caller.
 */
public final class DeterministicTest {
	private final ImmutableList<ControlledNode> nodes;
	private final ImmutableList<ECPublicKey> pks;
	private final ControlledNetwork network;

	private DeterministicTest(
		int numNodes,
		boolean enableGetVerticesRPC,
		BiFunction<CommittedStateSyncSender, EpochChangeSender, SyncedStateComputer<CommittedAtom>> stateComputerSupplier
	) {
		ImmutableList<ECKeyPair> keys = Stream.generate(ECKeyPair::generateNew)
			.limit(numNodes)
			.sorted(Comparator.<ECKeyPair, EUID>comparing(k -> k.getPublicKey().euid()).reversed())
			.collect(ImmutableList.toImmutableList());
		this.pks = keys.stream()
			.map(ECKeyPair::getPublicKey)
			.collect(ImmutableList.toImmutableList());
		this.network = new ControlledNetwork(pks);
		ValidatorSet initialValidatorSet = ValidatorSet.from(
			pks.stream().map(pk -> Validator.from(pk, UInt256.ONE)).collect(Collectors.toList())
		);

		this.nodes = Streams.mapWithIndex(keys.stream(),
			(key, index) -> {
				ControlledSender sender = network.getSender(key.getPublicKey());
				return new ControlledNode(
					"node-" + index,
					key,
					sender,
					vset -> new WeightedRotatingLeaders(vset, Comparator.comparing(v -> v.nodeKey().euid()), 5),
					initialValidatorSet,
					enableGetVerticesRPC,
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
			true,
			(committedSender, epochSender) -> new SingleEpochRandomlySyncedStateComputer(random, committedSender)
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
			true,
			(committedSender, epochChangeSender) -> SingleEpochAlwaysSyncedStateComputer.INSTANCE
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
			false,
			(committedSender, epochChangeSender) -> SingleEpochFailOnSyncStateComputer.INSTANCE
		);
	}

	public void start() {
		nodes.forEach(ControlledNode::start);
	}

	public void processNextMsg(int toIndex, int fromIndex, Class<?> expectedClass) {
		ChannelId channelId = new ChannelId(pks.get(fromIndex), pks.get(toIndex));
		Object msg = network.popNextMessage(channelId);
		assertThat(msg).isInstanceOf(expectedClass);
		nodes.get(toIndex).processNext(msg);
	}

	public void processNextMsg(Random random) {
		processNextMsg(random, (c, m) -> true);
	}

	public void processNextMsg(Random random, BiPredicate<Integer, Object> filter) {
		List<ControlledMessage> possibleMsgs = network.peekNextMessages();
		if (possibleMsgs.isEmpty()) {
			throw new IllegalStateException("No messages available (Lost Responsiveness)");
		}

		int nextIndex =  random.nextInt(possibleMsgs.size());
		ChannelId channelId = possibleMsgs.get(nextIndex).getChannelId();
		Object msg = network.popNextMessage(channelId);
		int receiverIndex = pks.indexOf(channelId.getReceiver());
		if (filter.test(receiverIndex, msg)) {
			nodes.get(receiverIndex).processNext(msg);
		}
	}

	public SystemCounters getSystemCounters(int nodeIndex) {
		return nodes.get(nodeIndex).getSystemCounters();
	}
}
