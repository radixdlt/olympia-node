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

package com.radixdlt.environment.deterministic.network;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Streams;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.utils.Pair;

import io.reactivex.rxjava3.schedulers.Timed;
import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A BFT network supporting the EventCoordinatorNetworkSender interface which
 * stores each message in a queue until they are synchronously popped.
 */
public final class DeterministicNetwork {
	private static final Logger log = LogManager.getLogger();
	private static final long DEFAULT_LATENCY = 50L; // virtual milliseconds
	private final MessageQueue messageQueue = new MessageQueue();
	private final MessageSelector messageSelector;
	private final MessageMutator messageMutator;

	private final ImmutableBiMap<BFTNode, Integer> nodeLookup;

	private long currentTime = 0L;

	/**
	 * Create a BFT test network for deterministic tests.
	 * @param nodes The nodes on the network
	 * @param messageSelector A {@link MessageSelector} for choosing messages to process next
	 * @param messageMutator A {@link MessageMutator} for mutating and queueing messages
	 */
	public DeterministicNetwork(
		List<BFTNode> nodes,
		MessageSelector messageSelector,
		MessageMutator messageMutator
	) {
		this.messageSelector = Objects.requireNonNull(messageSelector);
		this.messageMutator = Objects.requireNonNull(messageMutator);
		this.nodeLookup = Streams.mapWithIndex(
			nodes.stream(),
			(node, index) -> Pair.of(node, (int) index)
		).collect(ImmutableBiMap.toImmutableBiMap(Pair::getFirst, Pair::getSecond));

		log.debug("Nodes {}", this.nodeLookup);
	}

	public ControlledSender createSender(BFTNode node) {
		return new ControlledSender(this, node, this.lookup(node));
	}

	public ControlledSender createSender(int nodeIndex) {
		return new ControlledSender(this, this.lookup(nodeIndex), nodeIndex);
	}

	// TODO: use better method than Timed to store time
	public Timed<ControlledMessage> nextMessage() {
		List<ControlledMessage> controlledMessages = this.messageQueue.lowestTimeMessages();
		if (controlledMessages == null || controlledMessages.isEmpty()) {
			throw new IllegalStateException("No messages available (Lost Responsiveness)");
		}
		ControlledMessage controlledMessage = this.messageSelector.select(controlledMessages);

		this.messageQueue.remove(controlledMessage);
		this.currentTime = Math.max(this.currentTime, controlledMessage.arrivalTime());

		return new Timed<>(controlledMessage, this.currentTime, TimeUnit.MILLISECONDS);
	}

	public Timed<ControlledMessage> nextMessage(Predicate<ControlledMessage> predicate) {
		Set<ControlledMessage> allMessages = this.messageQueue.allMessages();
		ControlledMessage controlledMessage = allMessages.stream().filter(predicate).findAny().orElseThrow(
			() -> new IllegalStateException(String.format("Could not find message. Messages present: %s", allMessages))
		);
		this.messageQueue.remove(controlledMessage);
		this.currentTime = Math.max(this.currentTime, controlledMessage.arrivalTime());

		return new Timed<>(controlledMessage, this.currentTime, TimeUnit.MILLISECONDS);
	}

	public Set<ControlledMessage> allMessages() {
		return this.messageQueue.allMessages();
	}

	public void dropMessages(Predicate<ControlledMessage> controlledMessagePredicate) {
		this.messageQueue.remove(controlledMessagePredicate);
	}

	public long currentTime() {
		return this.currentTime;
	}

	public void dumpMessages(PrintStream out) {
		this.messageQueue.dump(out);
	}

	public int lookup(BFTNode node) {
		return this.nodeLookup.get(node);
	}

	public BFTNode lookup(int nodeIndex) {
		return this.nodeLookup.inverse().get(nodeIndex);
	}

	long delayForChannel(ChannelId channelId) {
		if (channelId.receiverIndex() == channelId.senderIndex()) {
			return 0L;
		}
		return DEFAULT_LATENCY;
	}

	void handleMessage(ControlledMessage controlledMessage) {
		log.debug("Sent message {}", controlledMessage);
		if (!this.messageMutator.mutate(controlledMessage, this.messageQueue)) {
			// If nothing processes this message, we just add it to the queue
			this.messageQueue.add(controlledMessage);
		}
	}
}
