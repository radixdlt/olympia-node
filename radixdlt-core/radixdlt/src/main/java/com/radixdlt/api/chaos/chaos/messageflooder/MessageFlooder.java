/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.api.chaos.chaos.messageflooder;

import com.google.inject.Inject;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;

import com.radixdlt.ledger.AccumulatorState;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Floods a node with proposal messages attempting to bring it down
 */
public final class MessageFlooder {

	private Logger logger = LogManager.getLogger();

	private final RemoteEventDispatcher<Proposal> proposalDispatcher;
	private final ScheduledEventDispatcher<ScheduledMessageFlood> scheduledFloodEventDispatcher;
	private final BFTNode self;

	private BFTNode nodeToAttack;
	private int messagesPerSec = 100;
	private int commandSize = 1024 * 1024;

	@Inject
	public MessageFlooder(
		@Self BFTNode self,
		RemoteEventDispatcher<Proposal> proposalDispatcher,
		ScheduledEventDispatcher<ScheduledMessageFlood> scheduledFloodEventDispatcher
	) {
		this.self = Objects.requireNonNull(self);
		this.proposalDispatcher = Objects.requireNonNull(proposalDispatcher);
		this.scheduledFloodEventDispatcher = Objects.requireNonNull(scheduledFloodEventDispatcher);
	}

	private Proposal createProposal() {
		var accumulatorState = new AccumulatorState(9, HashUtils.random256());
		var ledgerHeader = LedgerHeader.create(1, View.of(12345), accumulatorState, 300);
		var header = new BFTHeader(View.of(1), HashUtils.random256(), ledgerHeader);
		var voteData = new VoteData(header, header, header);
		var signatures = new TimestampedECDSASignatures();
		var qc = new QuorumCertificate(voteData, signatures);
		var vertex = UnverifiedVertex.create(
			qc, View.of(3), List.of(Txn.create(new byte[commandSize])), BFTNode.random()
		);

		return new Proposal(vertex, qc, ECDSASignature.zeroSignature(), Optional.empty());
	}

	public EventProcessor<MessageFlooderUpdate> messageFloodUpdateProcessor() {
		return msg -> {
			BFTNode nextNode = msg.getBFTNode().orElse(null);
			if (Objects.equals(this.nodeToAttack, nextNode)) {
				logger.info("Message flood no update: {}", nextNode);
				return;
			}

			logger.info("Message flood update: {}", nextNode);

			// Start flooding if we haven't started yet.
			if (this.nodeToAttack == null) {
				scheduledFloodEventDispatcher.dispatch(ScheduledMessageFlood.create(), 50);
			}

			this.messagesPerSec = msg.getMessagesPerSec().orElse(this.messagesPerSec);
			this.nodeToAttack = nextNode;
		};
	}

	public EventProcessor<ScheduledMessageFlood> scheduledMessageFloodProcessor() {
		return s -> {
			if (nodeToAttack == null) {
				return;
			}

			Proposal proposal = createProposal();
			for (int i = 0; i < messagesPerSec; i++) {
				proposalDispatcher.dispatch(Set.of(nodeToAttack), proposal);
			}

			scheduledFloodEventDispatcher.dispatch(ScheduledMessageFlood.create(), 1000);
		};
	}
}
