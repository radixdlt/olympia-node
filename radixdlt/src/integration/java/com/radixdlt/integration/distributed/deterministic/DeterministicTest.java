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

import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.integration.distributed.deterministic.configuration.EpochNodeWeightMapping;
import com.radixdlt.integration.distributed.deterministic.configuration.NodeIndexAndWeight;
import com.radixdlt.integration.distributed.deterministic.configuration.SyncedExecutorFactories;
import com.radixdlt.integration.distributed.deterministic.configuration.SyncedExecutorFactory;
import com.radixdlt.integration.distributed.deterministic.network.DeterministicNetwork;
import com.radixdlt.integration.distributed.deterministic.network.MessageMutator;
import com.radixdlt.integration.distributed.deterministic.network.MessageSelector;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.utils.UInt256;

import java.io.PrintStream;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.LongFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A deterministic test where each event that occurs in the network
 * is emitted and processed synchronously by the caller.
 */
public final class DeterministicTest {

	private final ImmutableList<BFTNode> nodes;
	private final MessageSelector messageSelector;
	private final MessageMutator messageMutator;
	private final DeterministicNetwork network;

	private DeterministicTest(
		ImmutableList<BFTNode> nodes,
		LongFunction<BFTValidatorSet> validatorSetMapping,
		MessageSelector messageSelector,
		MessageMutator messageMutator,
		SyncedExecutorFactory syncedExecutorFactory
	) {
		this.nodes = Objects.requireNonNull(nodes);
		this.messageSelector = Objects.requireNonNull(messageSelector);
		this.messageMutator = Objects.requireNonNull(messageMutator);

		// TODO only one epoch supported right now
		BFTValidatorSet validatorSet = validatorSetMapping.apply(1L);
		ImmutableList.Builder<Module> syncExecutionModules = ImmutableList.builder();
		syncExecutionModules.add(new DeterministicSyncExecutionModule(validatorSet, syncedExecutorFactory));
		this.network = new DeterministicNetwork(
			this.nodes,
			this.messageSelector,
			this.messageMutator,
			syncExecutionModules.build()
		);
	}

	public static class Builder {
		private ImmutableList<BFTNode> nodes = ImmutableList.of(BFTNode.create(ECKeyPair.generateNew().getPublicKey()));
		private MessageSelector messageSelector = MessageSelector.firstSelector();
		private MessageMutator messageMutator = MessageMutator.alwaysAdd(30_000L);
		private EpochNodeWeightMapping epochNodeWeightMapping;
		private SyncedExecutorFactory syncedExecutorFactory = SyncedExecutorFactories.alwaysSynced();

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

		public Builder epochNodeIndexesMapping(LongFunction<IntStream> epochToNodeIndexesMapping) {
			Objects.requireNonNull(epochToNodeIndexesMapping);
			this.epochNodeWeightMapping = epoch -> equalWeight(epochToNodeIndexesMapping.apply(epoch));
			return this;
		}

		public Builder epochNodeWeightMapping(EpochNodeWeightMapping epochNodeWeightMapping) {
			this.epochNodeWeightMapping = Objects.requireNonNull(epochNodeWeightMapping);
			return this;
		}

		public Builder messageSelector(MessageSelector messageSelector) {
			this.messageSelector = Objects.requireNonNull(messageSelector);
			return this;
		}

		public Builder messageMutator(MessageMutator messageMutator) {
			this.messageMutator = Objects.requireNonNull(messageMutator);
			return this;
		}

		public Builder syncedExecutorFactory(SyncedExecutorFactory syncedExecutorFactory) {
			this.syncedExecutorFactory = Objects.requireNonNull(syncedExecutorFactory);
			return this;
		}

		public DeterministicTest build() {
			LongFunction<BFTValidatorSet> validatorSetMapping = epochNodeWeightMapping == null
				? epoch -> completeEqualWeightValidatorSet(this.nodes)
				: epoch -> partialMixedWeightValidatorSet(epoch, this.nodes, this.epochNodeWeightMapping);
			return new DeterministicTest(
				this.nodes,
				validatorSetMapping,
				this.messageSelector,
				this.messageMutator,
				this.syncedExecutorFactory
			);
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
			EpochNodeWeightMapping mapper
		) {
			return BFTValidatorSet.from(
				mapper.nodesAndWeightFor(epoch)
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

	public DeterministicTest run() {
		this.network.run();
		return this;
	}

	public SystemCounters getSystemCounters(int nodeIndex) {
		return this.network.getSystemCounters(nodeIndex);
	}

	// Debugging aid for messages
	public void dumpMessages(PrintStream out) {
		this.network.dumpMessages(out);
	}
}
