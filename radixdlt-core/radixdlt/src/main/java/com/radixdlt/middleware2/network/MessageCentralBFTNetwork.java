/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.middleware2.network;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;

import com.radixdlt.environment.RemoteEventDispatcher;

import java.util.Objects;

import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.network.messaging.MessageFromPeer;
import com.radixdlt.network.p2p.NodeId;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.radix.network.messaging.Message;

import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.network.messaging.MessageCentral;

/**
 * BFT Network sending and receiving layer used on top of the MessageCentral
 * layer.
 */
public final class MessageCentralBFTNetwork {
	private final BFTNode self;
	private final int magic;
	private final MessageCentral messageCentral;
	private final PublishSubject<ConsensusEvent> localMessages;

	@Inject
	public MessageCentralBFTNetwork(
		@Self BFTNode self,
		@Named("magic") int magic,
		MessageCentral messageCentral
	) {
		this.magic = magic;
		this.self = Objects.requireNonNull(self);
		this.messageCentral = Objects.requireNonNull(messageCentral);
		this.localMessages = PublishSubject.create();
	}

	public Observable<Proposal> localProposals() {
		return localMessages.ofType(Proposal.class);
	}

	public Observable<Vote> localVotes() {
		return localMessages.ofType(Vote.class);
	}

	public Flowable<RemoteEvent<Vote>> remoteVotes() {
		return remoteBftEvents()
			.filter(m -> m.getMessage().getConsensusMessage() instanceof Vote)
			.map(m -> {
				final var node = BFTNode.create(m.getSource().getPublicKey());
				final var msg = m.getMessage();
				var vote = (Vote) msg.getConsensusMessage();
				return RemoteEvent.create(node, vote);
			});
	}

	public Flowable<RemoteEvent<Proposal>> remoteProposals() {
		return remoteBftEvents()
			.filter(m -> m.getMessage().getConsensusMessage() instanceof Proposal)
			.map(m -> {
				final var node = BFTNode.create(m.getSource().getPublicKey());
				final var msg = m.getMessage();
				var proposal = (Proposal) msg.getConsensusMessage();
				return RemoteEvent.create(node, proposal);
			});
	}

	private Flowable<MessageFromPeer<ConsensusEventMessage>> remoteBftEvents() {
		return this.messageCentral
			.messagesOf(ConsensusEventMessage.class)
			.toFlowable(BackpressureStrategy.BUFFER);
	}

	public RemoteEventDispatcher<Proposal> proposalDispatcher() {
		return this::sendProposal;
	}

	private void sendProposal(BFTNode receiver, Proposal proposal) {
		if (this.self.equals(receiver)) {
			this.localMessages.onNext(proposal);
		} else {
			ConsensusEventMessage message = new ConsensusEventMessage(this.magic, proposal);
			send(message, receiver);
		}
	}

	public RemoteEventDispatcher<Vote> voteDispatcher() {
		return this::sendVote;
	}

	private void sendVote(BFTNode receiver, Vote vote) {
		if (this.self.equals(receiver)) {
			this.localMessages.onNext(vote);
		} else {
			ConsensusEventMessage message = new ConsensusEventMessage(this.magic, vote);
			send(message, receiver);
		}
	}

	private void send(Message message, BFTNode recipient) {
		this.messageCentral.send(NodeId.fromPublicKey(recipient.getKey()), message);
	}
}
