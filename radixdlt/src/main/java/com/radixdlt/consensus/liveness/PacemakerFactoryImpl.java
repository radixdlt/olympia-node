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

import com.radixdlt.consensus.Vote;
import com.radixdlt.crypto.Hasher;
import java.util.Objects;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.network.TimeSupplier;

/**
 * @author msandiford
 *
 */
public class PacemakerFactoryImpl implements PacemakerFactory {

	private final BFTNode self;
	private final SystemCounters counters;
	private final ProposalBroadcaster proposalBroadcaster;
	private final NextCommandGenerator nextCommandGenerator;
	private final Hasher hasher;
	private RemoteEventDispatcher<Vote> voteDispatcher;
	private TimeSupplier timeSupplier;

	public PacemakerFactoryImpl(
		BFTNode self,
		SystemCounters counters,
		ProposalBroadcaster proposalBroadcaster,
		NextCommandGenerator nextCommandGenerator,
		Hasher hasher,
		RemoteEventDispatcher<Vote> voteDispatcher,
		TimeSupplier timeSupplier
	) {
		this.self = Objects.requireNonNull(self);
		this.counters = Objects.requireNonNull(counters);
		this.proposalBroadcaster = Objects.requireNonNull(proposalBroadcaster);
		this.nextCommandGenerator = Objects.requireNonNull(nextCommandGenerator);
		this.hasher = Objects.requireNonNull(hasher);
		this.voteDispatcher = Objects.requireNonNull(voteDispatcher);
		this.timeSupplier = Objects.requireNonNull(timeSupplier);
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
		return new Pacemaker(
			self,
			counters,
			validatorSet,
			vertexStore,
			safetyRules,
			infoSender,
			pacemakerState,
			timeoutSender,
			timeoutCalculator,
			nextCommandGenerator,
			proposalBroadcaster,
			proposerElection,
			hasher,
			voteDispatcher,
			timeSupplier
		);
	}
}
