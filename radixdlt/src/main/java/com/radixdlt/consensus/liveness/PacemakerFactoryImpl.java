/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.liveness;

import java.util.Objects;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.Hasher;

/**
 * @author msandiford
 *
 */
public class PacemakerFactoryImpl implements PacemakerFactory {

	private final BFTNode self;
	private final SystemCounters counters;
	private final VoteSender voteSender;
	private final ProposalBroadcaster proposalBroadcaster;
	private final NextCommandGenerator nextCommandGenerator;
	private final Hasher hasher;

	public PacemakerFactoryImpl(
		BFTNode self,
		SystemCounters counters,
		VoteSender voteSender,
		ProposalBroadcaster proposalBroadcaster,
		NextCommandGenerator nextCommandGenerator,
		Hasher hasher
	) {
		this.self = Objects.requireNonNull(self);
		this.counters = Objects.requireNonNull(counters);
		this.voteSender = Objects.requireNonNull(voteSender);
		this.proposalBroadcaster = Objects.requireNonNull(proposalBroadcaster);
		this.nextCommandGenerator = Objects.requireNonNull(nextCommandGenerator);
		this.hasher = Objects.requireNonNull(hasher);
	}

	@Override
	public Pacemaker create(
		BFTValidatorSet validatorSet,
		VertexStore vertexStore,
		PacemakerInfoSender infoSender,
		PacemakerState pacemakerState,
		PacemakerTimeoutSender timeoutSender,
		PacemakerTimeoutCalculator timeoutCalculator,
		SafetyRules safetyRules,
		ProposerElection proposerElection
	) {
		PendingViewTimeouts pendingViewTimeouts = new PendingViewTimeouts();
		return new Pacemaker(
			self,
			counters,
			pendingViewTimeouts,
			validatorSet,
			vertexStore,
			safetyRules,
			voteSender,
			infoSender,
			pacemakerState,
			timeoutSender,
			timeoutCalculator,
			nextCommandGenerator,
			proposalBroadcaster,
			proposerElection,
			hasher
		);
	}
}
