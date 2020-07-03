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
import com.radixdlt.consensus.deterministic.ControlledBFTNetwork.ChannelId;
import com.radixdlt.consensus.deterministic.ControlledBFTNetwork.ControlledMessage;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.utils.UInt256;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A deterministic BFT test where each event that occurs in the BFT network
 * is emitted and processed synchronously by the caller.
 */
public class BFTDeterministicTest {
	private final ImmutableList<ControlledBFTNode> nodes;
	private final ImmutableList<ECPublicKey> pks;
	private final ControlledBFTNetwork network;

	public BFTDeterministicTest(int numNodes, boolean enableGetVerticesRPC) {
		this(numNodes, enableGetVerticesRPC, () -> {
			throw new UnsupportedOperationException();
		});
	}

	public BFTDeterministicTest(int numNodes, boolean enableGetVerticesRPC, BooleanSupplier syncedSupplier) {
		ImmutableList<ECKeyPair> keys = Stream.generate(ECKeyPair::generateNew)
			.limit(numNodes)
			.sorted(Comparator.<ECKeyPair, EUID>comparing(k -> k.getPublicKey().euid()).reversed())
			.collect(ImmutableList.toImmutableList());
		this.pks = keys.stream()
			.map(ECKeyPair::getPublicKey)
			.collect(ImmutableList.toImmutableList());
		this.network = new ControlledBFTNetwork(pks);
		ValidatorSet initialValidatorSet = ValidatorSet.from(
			pks.stream().map(pk -> Validator.from(pk, UInt256.ONE)).collect(Collectors.toList())
		);

		this.nodes = keys.stream()
			.map(key -> new ControlledBFTNode(
				key,
				network.getSender(key.getPublicKey()),
				vset -> new WeightedRotatingLeaders(vset, Comparator.comparing(v -> v.nodeKey().euid()), 5),
				initialValidatorSet,
				enableGetVerticesRPC,
				syncedSupplier
			))
			.collect(ImmutableList.toImmutableList());
	}

	public void start() {
		nodes.forEach(ControlledBFTNode::start);
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
