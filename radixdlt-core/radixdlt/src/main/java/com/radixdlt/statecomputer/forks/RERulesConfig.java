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
 *
 */

package com.radixdlt.statecomputer.forks;

import com.radixdlt.application.tokens.Amount;

import java.util.OptionalInt;

public final class RERulesConfig {
	private final long maxRounds;
	private final OptionalInt maxSigsPerRound;
	private final boolean fees;
	private final long rakeIncreaseDebouncerEpochLength;
	private final Amount minimumStake;
	private final long unstakingEpochDelay;
	private final Amount rewardsPerProposal;
	private final int minimumCompletedProposalsPercentage;

	public RERulesConfig(
		boolean fees,
		OptionalInt maxSigsPerRound,
		long maxRounds,
		long rakeIncreaseDebouncerEpochLength,
		Amount minimumStake,
		long unstakingEpochDelay,
		Amount rewardsPerProposal,
		int minimumCompletedProposalsPercentage
	) {
		this.fees = fees;
		this.maxSigsPerRound = maxSigsPerRound;
		this.maxRounds = maxRounds;
		this.rakeIncreaseDebouncerEpochLength = rakeIncreaseDebouncerEpochLength;
		this.minimumStake = minimumStake;
		this.unstakingEpochDelay = unstakingEpochDelay;
		this.rewardsPerProposal = rewardsPerProposal;
		this.minimumCompletedProposalsPercentage = minimumCompletedProposalsPercentage;
	}

	public static RERulesConfig testingDefault() {
		return new RERulesConfig(
			false,
			OptionalInt.of(2),
			10,
			1,
			Amount.ofTokens(10),
			1,
			Amount.ofTokens(10),
			9800
		);
	}

	public OptionalInt getMaxSigsPerRound() {
		return maxSigsPerRound;
	}

	public Amount getMinimumStake() {
		return minimumStake;
	}

	public long getRakeIncreaseDebouncerEpochLength() {
		return rakeIncreaseDebouncerEpochLength;
	}

	public boolean includeFees() {
		return fees;
	}

	public long getMaxRounds() {
		return maxRounds;
	}

	public Amount getRewardsPerProposal() {
		return rewardsPerProposal;
	}

	public long getUnstakingEpochDelay() {
		return unstakingEpochDelay;
	}

	public int getMinimumCompletedProposalsPercentage() {
		return minimumCompletedProposalsPercentage;
	}

	public RERulesConfig overrideMaxSigsPerRound(int maxSigsPerRound) {
		return new RERulesConfig(
			this.fees,
			OptionalInt.of(maxSigsPerRound),
			this.maxRounds,
			this.rakeIncreaseDebouncerEpochLength,
			this.minimumStake,
			this.unstakingEpochDelay,
			this.rewardsPerProposal,
			this.minimumCompletedProposalsPercentage
		);
	}

	public RERulesConfig removeSigsPerRoundLimit() {
		return new RERulesConfig(
			this.fees,
			OptionalInt.empty(),
			this.maxRounds,
			this.rakeIncreaseDebouncerEpochLength,
			this.minimumStake,
			this.unstakingEpochDelay,
			this.rewardsPerProposal,
			this.minimumCompletedProposalsPercentage
		);
	}

	public RERulesConfig overrideFees(boolean fees) {
		return new RERulesConfig(
			fees,
			this.maxSigsPerRound,
			this.maxRounds,
			this.rakeIncreaseDebouncerEpochLength,
			this.minimumStake,
			this.unstakingEpochDelay,
			this.rewardsPerProposal,
			this.minimumCompletedProposalsPercentage
		);
	}

	public RERulesConfig overrideMaxRounds(long maxRounds) {
		return new RERulesConfig(
			this.fees,
			this.maxSigsPerRound,
			maxRounds,
			this.rakeIncreaseDebouncerEpochLength,
			this.minimumStake,
			this.unstakingEpochDelay,
			this.rewardsPerProposal,
			this.minimumCompletedProposalsPercentage
		);
	}
}
