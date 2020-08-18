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

package com.radixdlt.integration.distributed.deterministic.network;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.bft.VertexStore.SyncVerticesRPCSender;
import com.radixdlt.consensus.bft.VertexStore.SyncedVertexSender;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.integration.distributed.deterministic.DeterministicConsensusModule;
import com.radixdlt.integration.distributed.deterministic.DeterministicConsensusRunner;
import com.radixdlt.integration.distributed.deterministic.DeterministicNetworkModule;
import com.radixdlt.integration.distributed.simulation.MockedCryptoModule;
import com.radixdlt.syncer.EpochChangeSender;
import com.radixdlt.syncer.SyncExecutor.CommittedStateSyncSender;
import com.radixdlt.utils.Pair;

import java.io.PrintStream;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * A BFT network supporting the EventCoordinatorNetworkSender interface which
 * stores each message in a queue until they are synchronously popped.
 */
public final class DeterministicNetwork {

	public interface DeterministicSender extends
		BFTEventSender,
		VertexStoreEventSender,
		SyncVerticesRPCSender,
		SyncedVertexSender,
		EpochChangeSender,
		CommittedStateSyncSender,
		LocalTimeoutSender,
		SyncEpochsRPCSender {
		// Aggregation, no additional stuff
	}

	private final MessageQueue messageQueue = new MessageQueue();
	private final MessageSelector messageSelector;
	private final MessageMutator messageMutator;

	private final ImmutableBiMap<BFTNode, Integer> nodeLookup;
	private final ImmutableList<Injector> nodeInstances;
	private final AtomicBoolean running = new AtomicBoolean(false);

	/**
	 * Create a BFT test network for deterministic tests.
	 * @param nodes The nodes on the network
	 * @param messageSelector A {@link MessageSelector} for choosing messages to process next
	 * @param messageMutator A {@link MessageMutator} for mutating and queueing messages
	 * @param syncExecutionModules Guice modules to use for specifying sync execution sub-system
	 */
	public DeterministicNetwork(
		List<BFTNode> nodes,
		MessageSelector messageSelector,
		MessageMutator messageMutator,
		Collection<Module> syncExecutionModules
	) {
		this.messageSelector = Objects.requireNonNull(messageSelector);
		this.messageMutator = Objects.requireNonNull(messageMutator);
		this.nodeLookup = Streams.mapWithIndex(nodes.stream(), (node, index) -> Pair.of(node, (int) index))
			.collect(ImmutableBiMap.toImmutableBiMap(Pair::getFirst, Pair::getSecond));
		this.nodeInstances = nodes.stream()
			.map(node -> createBFTInstance(node, syncExecutionModules))
			.collect(ImmutableList.toImmutableList());
	}

	/**
	 * Create the network se
	 * @param node
	 * @return
	 */
	public DeterministicSender createSender(BFTNode node) {
		return new ControlledSender(this, lookup(node));
	}

	public void run() {
		List<DeterministicConsensusRunner> consensusRunners = this.nodeInstances.stream()
			.map(i -> i.getInstance(DeterministicConsensusRunner.class))
			.collect(Collectors.toList());

		consensusRunners.forEach(DeterministicConsensusRunner::start);

		this.running.set(true);
		while (this.running.get()) {
			List<ControlledMessage> controlledMessages = this.messageQueue.lowestRankMessages();
			if (controlledMessages.isEmpty()) {
				throw new IllegalStateException("No messages available (Lost Responsiveness)");
			}
			ControlledMessage controlledMessage = this.messageSelector.select(controlledMessages);
			this.messageQueue.remove(controlledMessage);
			consensusRunners.get(controlledMessage.channelId().receiverIndex()).handleMessage(controlledMessage.message());
		}
	}

	public void stop() {
		this.running.set(false);
	}

	public int lookup(BFTNode node) {
		return this.nodeLookup.get(node);
	}

	public SystemCounters getSystemCounters(int nodeIndex) {
		return this.nodeInstances.get(nodeIndex).getInstance(SystemCounters.class);
	}

	public void dumpMessages(PrintStream out) {
		this.messageQueue.dump(out);
	}

	void handleMessage(MessageRank eav, ControlledMessage controlledMessage) {
		if (!this.messageMutator.mutate(eav, controlledMessage, this.messageQueue)) {
			stop();
		}
	}

	private Injector createBFTInstance(BFTNode self, Collection<Module> syncExecutionModules) {
		List<Module> modules = ImmutableList.of(
			new DeterministicConsensusModule(),
			new MockedCryptoModule(),
			new DeterministicNetworkModule(self, createSender(self))
		);
		return Guice.createInjector(Iterables.concat(modules, syncExecutionModules));
	}
}
