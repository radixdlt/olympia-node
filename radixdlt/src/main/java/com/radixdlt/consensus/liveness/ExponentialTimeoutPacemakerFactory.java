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

import com.radixdlt.crypto.Hasher;
import java.util.Objects;

import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.liveness.ExponentialTimeoutPacemaker.PacemakerInfoSender;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.counters.SystemCounters;

/**
 * @author msandiford
 *
 */
public class ExponentialTimeoutPacemakerFactory implements PacemakerFactory {

	private final long timeoutMilliseconds;
	private final double rate;
	private final int maxExponent;
	private final BFTNode self;
	private final SystemCounters counters;
	private final NextCommandGenerator nextCommandGenerator;
	private final Hasher hasher;
	private final HashSigner signer;
	private final ProposalBroadcaster proposalBroadcaster;
	private final VoteSender voteSender;

	public ExponentialTimeoutPacemakerFactory(
		long timeoutMilliseconds,
		double rate,
		int maxExponent,
		BFTNode self,
		SystemCounters counters,
		NextCommandGenerator nextCommandGenerator,
		Hasher hasher,
		HashSigner signer,
		ProposalBroadcaster proposalBroadcaster,
		VoteSender voteSender
	) {
		this.timeoutMilliseconds = timeoutMilliseconds;
		this.rate = rate;
		this.maxExponent = maxExponent;
		this.self = Objects.requireNonNull(self);
		this.counters = Objects.requireNonNull(counters);
		this.nextCommandGenerator = Objects.requireNonNull(nextCommandGenerator);
		this.hasher = Objects.requireNonNull(hasher);
		this.signer = Objects.requireNonNull(signer);
		this.proposalBroadcaster = Objects.requireNonNull(proposalBroadcaster);
		this.voteSender = Objects.requireNonNull(voteSender);

	}

	@Override
	public ExponentialTimeoutPacemaker create(
		BFTValidatorSet validatorSet,
		VertexStore vertexStore,
		ProposerElection proposerElection,
		PacemakerTimeoutSender timeoutSender,
		PacemakerInfoSender infoSender
	) {
		PendingVotes pendingVotes = new PendingVotes(hasher);
		PendingViewTimeouts pendingViewTimeouts = new PendingViewTimeouts();
		SafetyRules safetyRules = new SafetyRules(self, SafetyState.initialState(), hasher, signer);
		return new ExponentialTimeoutPacemaker(
			timeoutMilliseconds,
			rate,
			maxExponent,
			self,
			counters,
			pendingVotes,
			pendingViewTimeouts,
			validatorSet,
			vertexStore,
			proposerElection,
			safetyRules,
			nextCommandGenerator,
			hasher,
			proposalBroadcaster, voteSender,
			timeoutSender,
			infoSender
		);
	}
}
