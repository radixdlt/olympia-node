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

package com.radixdlt.middleware2.network;

import com.google.inject.name.Named;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.sync.messages.remote.LedgerStatusUpdate;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import java.util.Objects;
import javax.inject.Inject;

/**
 * Network interface for syncing committed state using the MessageCentral
 */
public final class MessageCentralLedgerSync {
	private final int magic;
	private final MessageCentral messageCentral;

	@Inject
	public MessageCentralLedgerSync(
		@Named("magic") int magic,
		MessageCentral messageCentral
	) {
		this.magic = magic;
		this.messageCentral = Objects.requireNonNull(messageCentral);
	}

	public Flowable<RemoteEvent<StatusRequest>> statusRequests() {
		return this.messageCentral.messagesOf(StatusRequestMessage.class)
			.toFlowable(BackpressureStrategy.BUFFER)
			.map(m -> {
				final var node = BFTNode.create(m.getSource().getPublicKey());
				return RemoteEvent.create(node, StatusRequest.create());
			});
	}

	public Flowable<RemoteEvent<StatusResponse>> statusResponses() {
		return this.messageCentral.messagesOf(StatusResponseMessage.class)
			.toFlowable(BackpressureStrategy.BUFFER)
			.map(m -> {
				final var node = BFTNode.create(m.getSource().getPublicKey());
				final var msg = m.getMessage();
				return RemoteEvent.create(node, StatusResponse.create(msg.getHeader()));
			});
	}

	public Flowable<RemoteEvent<SyncRequest>> syncRequests() {
		return this.messageCentral.messagesOf(SyncRequestMessage.class)
			.toFlowable(BackpressureStrategy.BUFFER)
			.map(m -> {
				final var node = BFTNode.create(m.getSource().getPublicKey());
				final var msg = m.getMessage();
				return RemoteEvent.create(node, SyncRequest.create(msg.getCurrentHeader()));
			});
	}

	public Flowable<RemoteEvent<SyncResponse>> syncResponses() {
		return this.messageCentral.messagesOf(SyncResponseMessage.class)
			.toFlowable(BackpressureStrategy.BUFFER)
			.map(m -> {
				final var node = BFTNode.create(m.getSource().getPublicKey());
				final var msg = m.getMessage();
				return RemoteEvent.create(node, SyncResponse.create(msg.getCommands()));
			});
	}

	public Flowable<RemoteEvent<LedgerStatusUpdate>> ledgerStatusUpdates() {
		return this.messageCentral.messagesOf(LedgerStatusUpdateMessage.class)
			.toFlowable(BackpressureStrategy.BUFFER)
			.map(m -> {
				final var node = BFTNode.create(m.getSource().getPublicKey());
				final var header = m.getMessage().getHeader();
				return RemoteEvent.create(node, LedgerStatusUpdate.create(header));
			});
	}

	public RemoteEventDispatcher<SyncRequest> syncRequestDispatcher() {
		return this::sendSyncRequest;
	}

	private void sendSyncRequest(BFTNode node, SyncRequest syncRequest) {
		final var msg = new SyncRequestMessage(this.magic, syncRequest.getHeader());
		this.messageCentral.send(NodeId.fromPublicKey(node.getKey()), msg);
	}

	public RemoteEventDispatcher<SyncResponse> syncResponseDispatcher() {
		return this::sendSyncResponse;
	}

	private void sendSyncResponse(BFTNode node, SyncResponse syncResponse) {
		final var msg = new SyncResponseMessage(this.magic, syncResponse.getTxnsAndProof());
		this.messageCentral.send(NodeId.fromPublicKey(node.getKey()), msg);
	}

	public RemoteEventDispatcher<StatusRequest> statusRequestDispatcher() {
		return this::sendStatusRequest;
	}

	private void sendStatusRequest(BFTNode node, StatusRequest statusRequest) {
		final var msg = new StatusRequestMessage(this.magic);
		this.messageCentral.send(NodeId.fromPublicKey(node.getKey()), msg);
	}

	public RemoteEventDispatcher<StatusResponse> statusResponseDispatcher() {
		return this::sendStatusResponse;
	}

	private void sendStatusResponse(BFTNode node, StatusResponse statusResponse) {
		final var msg = new StatusResponseMessage(this.magic, statusResponse.getHeader());
		this.messageCentral.send(NodeId.fromPublicKey(node.getKey()), msg);
	}

	public RemoteEventDispatcher<LedgerStatusUpdate> ledgerStatusUpdateDispatcher() {
		return this::sendLedgerStatusUpdate;
	}

	private void sendLedgerStatusUpdate(BFTNode node, LedgerStatusUpdate ledgerStatusUpdate) {
		final var msg = new LedgerStatusUpdateMessage(this.magic, ledgerStatusUpdate.getHeader());
		this.messageCentral.send(NodeId.fromPublicKey(node.getKey()), msg);
	}
}
