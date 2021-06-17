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
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.p2p.NodeId;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network.messaging.Message;

import javax.inject.Inject;
import java.util.Objects;

/**
 * Network layer for the mempool
 */
public final class MessageCentralMempool {
	private static final Logger log = LogManager.getLogger();

	private final MessageCentral messageCentral;
	private final int magic;

	@Inject
	public MessageCentralMempool(
		@Named("magic") int magic,
		MessageCentral messageCentral
	) {
		this.magic = magic;
		this.messageCentral = Objects.requireNonNull(messageCentral);
	}

	public RemoteEventDispatcher<MempoolAdd> mempoolAddRemoteEventDispatcher() {
		return (receiver, msg) -> {
			MempoolAddMessage message = new MempoolAddMessage(this.magic, msg.getTxns());
			this.send(message, receiver);
		};
	}

	private void send(Message message, BFTNode recipient) {
		this.messageCentral.send(NodeId.fromPublicKey(recipient.getKey()), message);
	}

	public Flowable<RemoteEvent<MempoolAdd>> mempoolComands() {
		return messageCentral
			.messagesOf(MempoolAddMessage.class)
			.map(msg -> {
				final BFTNode node = BFTNode.create(msg.getSource().getPublicKey());
				return RemoteEvent.create(
					node,
					MempoolAdd.create(msg.getMessage().getTxns())
				);
			})
			.toFlowable(BackpressureStrategy.BUFFER);
	}
}
