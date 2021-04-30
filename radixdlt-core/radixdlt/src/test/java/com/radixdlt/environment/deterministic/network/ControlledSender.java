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

import com.google.inject.TypeLiteral;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import java.util.Set;

import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork.DeterministicSender;

/**
 * A sender within a deterministic network.
 */
public final class ControlledSender implements DeterministicSender, Environment {
	private final DeterministicNetwork network;
	private final BFTNode self;
	private final int senderIndex;
	private final ChannelId localChannel;


	ControlledSender(DeterministicNetwork network, BFTNode self, int senderIndex) {
		this.network = network;
		this.self = self;
		this.senderIndex = senderIndex;
		this.localChannel = ChannelId.of(this.senderIndex, this.senderIndex);
	}

	@Override
	public void sendGetVerticesErrorResponse(BFTNode node, HighQC highQC, GetVerticesRequest request) {
		GetVerticesErrorResponse response = new GetVerticesErrorResponse(this.self, highQC, request);
		ChannelId channelId = ChannelId.of(this.senderIndex, this.network.lookup(node));
		handleMessage(new ControlledMessage(self, channelId, response, arrivalTime(channelId)));
	}

	@Override
	public void broadcastProposal(Proposal proposal, Set<BFTNode> nodes) {
		for (BFTNode node : nodes) {
			int receiverIndex = this.network.lookup(node);
			ChannelId channelId = ChannelId.of(this.senderIndex, receiverIndex);
			handleMessage(new ControlledMessage(self, channelId, proposal, arrivalTime(channelId)));
		}
	}

	@Override
	public <T> EventDispatcher<T> getDispatcher(Class<T> eventClass) {
		return e -> handleMessage(new ControlledMessage(self, this.localChannel, e, arrivalTime(this.localChannel)));
	}

	@Override
	public <T> ScheduledEventDispatcher<T> getScheduledDispatcher(Class<T> eventClass) {
		return (t, milliseconds) -> {
			ControlledMessage msg = new ControlledMessage(self, this.localChannel, t, arrivalTime(this.localChannel) + milliseconds);
			handleMessage(msg);
		};
	}

	@Override
	public <T> ScheduledEventDispatcher<T> getScheduledDispatcher(TypeLiteral<T> typeLiteral) {
		return (t, milliseconds) -> {
			ControlledMessage msg = new ControlledMessage(self, this.localChannel, t, arrivalTime(this.localChannel) + milliseconds);
			handleMessage(msg);
		};
	}

	@Override
	public <T> RemoteEventDispatcher<T> getRemoteDispatcher(Class<T> eventClass) {
		return (node, e) -> {
			ChannelId channelId = ChannelId.of(this.senderIndex, this.network.lookup(node));
			handleMessage(new ControlledMessage(self, channelId, e, arrivalTime(channelId)));
		};
	}

	private void handleMessage(ControlledMessage controlledMessage) {
		this.network.handleMessage(controlledMessage);
	}

	private long arrivalTime(ChannelId channelId) {
		long delay = this.network.delayForChannel(channelId);
		return this.network.currentTime() + delay;
	}
}
