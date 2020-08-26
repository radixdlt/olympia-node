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
import com.radixdlt.LedgerEpochChangeModule;
import com.radixdlt.LedgerLocalMempoolModule;
import com.radixdlt.LedgerModule;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.integration.distributed.deterministic.configuration.EpochNodeWeightMapping;
import com.radixdlt.integration.distributed.deterministic.configuration.NodeIndexAndWeight;
import com.radixdlt.integration.distributed.deterministic.network.DeterministicNetwork;
import com.radixdlt.integration.distributed.deterministic.network.MessageMutator;
import com.radixdlt.integration.distributed.deterministic.network.MessageSelector;
import com.radixdlt.integration.distributed.simulation.MockedStateComputerWithEpochsModule;
import com.radixdlt.integration.distributed.simulation.MockedStateComputerModule;
import com.radixdlt.integration.distributed.simulation.MockedSyncServiceModule;
import com.radixdlt.ledger.CommittedCommand;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.utils.UInt256;

import java.io.PrintStream;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A deterministic test where each event that occurs in the network
 * is emitted and processed synchronously by the caller.
 */
public final class DeterministicTest {
	private final DeterministicNetwork network;

	private DeterministicTest(
		ImmutableList<BFTNode> nodes,
		MessageSelector messageSelector,
		MessageMutator messageMutator,
		Collection<Module> modules
	) {
		this.network = new DeterministicNetwork(
			nodes,
			messageSelector,
			messageMutator,
			modules
		);
	}

	public static class Builder {
		private ImmutableList<BFTNode> nodes = ImmutableList.of(BFTNode.create(ECKeyPair.generateNew().getPublicKey()));
		private MessageSelector messageSelector = MessageSelector.selectAndStopAfter(MessageSelector.firstSelector(), 30_000L);
		private MessageMutator messageMutator = MessageMutator.nothing();
		private EpochNodeWeightMapping epochNodeWeightMapping = null;
		private Module syncedExecutorModule = null;
		private View epochHighView = null;

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

		public Builder alwaysSynced() {
			this.syncedExecutorModule = new DeterministicAlwaysSyncedLedgerModule();
			return this;
		}

		public Builder randomlySynced(Random random) {
			Objects.requireNonNull(random);
			this.syncedExecutorModule = new DeterministicRandomlySyncedLedgerModule(random);
			return this;
		}

		public Builder epochHighView(View epochHighView) {
			Objects.requireNonNull(epochHighView);
			this.epochHighView = epochHighView;
			return this;
		}

		public DeterministicTest build() {
			if (this.syncedExecutorModule == null && epochHighView == null) {
				throw new IllegalArgumentException("Must specify one (and only one) of alwaysSynced, randomlySynced or epochHighView");
			}
			if (this.syncedExecutorModule != null && epochHighView != null) {
				throw new IllegalArgumentException("Can only specify one of alwaysSynced, randomlySynced or epochHighView");
			}
			LongFunction<BFTValidatorSet> validatorSetMapping = epochNodeWeightMapping == null
				? epoch -> completeEqualWeightValidatorSet(this.nodes)
				: epoch -> partialMixedWeightValidatorSet(epoch, this.nodes, this.epochNodeWeightMapping);

			ConcurrentMap<Long, CommittedCommand> sharedCommittedAtoms = new ConcurrentHashMap<>();
			ImmutableList.Builder<Module> modules = ImmutableList.builder();
			modules.add(new LedgerModule());
			modules.add(new LedgerLocalMempoolModule(10));
			modules.add(new DeterministicMempoolModule());

			if (epochHighView == null) {
				BFTValidatorSet validatorSet = validatorSetMapping.apply(1L);
				modules.add(new MockedStateComputerModule(validatorSet));
				modules.add(this.syncedExecutorModule);
			} else {
				// TODO: adapter from LongFunction<BFTValidatorSet> to Function<Long, BFTValidatorSet> shouldn't be needed
				Function<Long, BFTValidatorSet> epochToValidatorSetMapping = validatorSetMapping::apply;
				modules.add(new LedgerEpochChangeModule());
				modules.add(new MockedSyncServiceModule(sharedCommittedAtoms));
				modules.add(new MockedStateComputerWithEpochsModule(epochHighView, epochToValidatorSetMapping));
			}
			return new DeterministicTest(
				this.nodes,
				this.messageSelector,
				this.messageMutator,
				modules.build()
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

	public int numNodes() {
		return this.network.numNodes();
	}

	// Debugging aid for messages
	public void dumpMessages(PrintStream out) {
		this.network.dumpMessages(out);
	}
}
