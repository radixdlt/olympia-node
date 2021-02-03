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

package com.radixdlt.chaos;

import com.google.inject.Inject;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VoteData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;

import com.radixdlt.ledger.AccumulatorState;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Floods a node with proposal messages attempting to bring it down
 */
public final class BFTNodeMessageFlooder {
	private static final int NUM_MESSAGES_PER_SECOND = 100;
	private static final int COMMAND_SIZE = 1024 * 1024;
	private Logger logger = LogManager.getLogger();

	private final ProposalBroadcaster proposalBroadcaster;
	private final ScheduledEventDispatcher<ScheduledMessageFlood> scheduledFloodEventDispatcher;
	private final BFTNode self;
	private BFTNode nodeToAttack;

	@Inject
	public BFTNodeMessageFlooder(
		@Self BFTNode self,
		ProposalBroadcaster proposalBroadcaster,
        ScheduledEventDispatcher<ScheduledMessageFlood> scheduledFloodEventDispatcher
	) {
		this.self = Objects.requireNonNull(self);
		this.proposalBroadcaster = Objects.requireNonNull(proposalBroadcaster);
		this.scheduledFloodEventDispatcher = Objects.requireNonNull(scheduledFloodEventDispatcher);
	}

	private Proposal createProposal() {
		AccumulatorState accumulatorState = new AccumulatorState(9, HashUtils.random256());
		LedgerHeader ledgerHeader = LedgerHeader.create(1, View.of(12345), accumulatorState, 300);
		BFTHeader header = new BFTHeader(View.of(1), HashUtils.random256(), ledgerHeader);
		VoteData voteData = new VoteData(header, header, header);
		TimestampedECDSASignatures signatures = new TimestampedECDSASignatures();
		QuorumCertificate qc = new QuorumCertificate(voteData, signatures);
		Command command = new Command(new byte[COMMAND_SIZE]);
		UnverifiedVertex vertex = UnverifiedVertex.createVertex(qc, View.of(3), command);

		return new Proposal(vertex, qc, self, new ECDSASignature(), Optional.empty());
	}

	public EventProcessor<MessageFloodUpdate> messageFloodUpdateProcessor() {
		return msg -> {
			BFTNode nextNode = msg.getBFTNode().orElse(null);
		    if (Objects.equals(this.nodeToAttack, nextNode)) {
				logger.info("Message flood no update: {}", nextNode);
		    	return;
			}

			logger.info("Message flood update: {}", nextNode);

			if (this.nodeToAttack == null) {
		    	scheduledFloodEventDispatcher.dispatch(ScheduledMessageFlood.create(), 50);
			}

			this.nodeToAttack = nextNode;
		};
	}

	public EventProcessor<ScheduledMessageFlood> scheduledMessageFloodProcessor() {
		return s -> {
			if (nodeToAttack == null) {
				return;
			}

			Proposal proposal = createProposal();
			for (int i = 0; i < NUM_MESSAGES_PER_SECOND; i++) {
				proposalBroadcaster.broadcastProposal(proposal, Set.of(nodeToAttack));
			}

			scheduledFloodEventDispatcher.dispatch(ScheduledMessageFlood.create(), 1000);
		};
	}
}
