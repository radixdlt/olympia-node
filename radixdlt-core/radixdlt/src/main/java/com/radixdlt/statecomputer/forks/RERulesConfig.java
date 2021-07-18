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
import com.radixdlt.application.system.FeeTable;

import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

public final class RERulesConfig {
	private final Set<String> reservedSymbols;
	private final FeeTable feeTable;
	private final long maxRounds;
	private final OptionalInt maxSigsPerRound;
	private final long rakeIncreaseDebouncerEpochLength;
	private final Amount minimumStake;
	private final long unstakingEpochDelay;
	private final Amount rewardsPerProposal;
	private final int minimumCompletedProposalsPercentage;
	private final int maxValidators;

	public RERulesConfig(
		Set<String> reservedSymbols,
		FeeTable feeTable,
		OptionalInt maxSigsPerRound,
		long maxRounds,
		long rakeIncreaseDebouncerEpochLength,
		Amount minimumStake,
		long unstakingEpochDelay,
		Amount rewardsPerProposal,
		int minimumCompletedProposalsPercentage,
		int maxValidators
	) {
		this.reservedSymbols = reservedSymbols;
		this.feeTable = feeTable;
		this.maxSigsPerRound = maxSigsPerRound;
		this.maxRounds = maxRounds;
		this.rakeIncreaseDebouncerEpochLength = rakeIncreaseDebouncerEpochLength;
		this.minimumStake = minimumStake;
		this.unstakingEpochDelay = unstakingEpochDelay;
		this.rewardsPerProposal = rewardsPerProposal;
		this.minimumCompletedProposalsPercentage = minimumCompletedProposalsPercentage;
		this.maxValidators = maxValidators;
	}

	public static RERulesConfig testingDefault() {
		return new RERulesConfig(
			Set.of("xrd"),
			FeeTable.create(Amount.zero(), Map.of()),
			OptionalInt.of(2),
			10,
			1,
			Amount.ofTokens(10),
			1,
			Amount.ofTokens(10),
			9800,
			10
		);
	}

	public Set<String> getReservedSymbols() {
		return reservedSymbols;
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

	public FeeTable getFeeTable() {
		return feeTable;
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

	public int getMaxValidators() {
		return maxValidators;
	}

	public RERulesConfig overrideMaxSigsPerRound(int maxSigsPerRound) {
		return new RERulesConfig(
			this.reservedSymbols,
			this.feeTable,
			OptionalInt.of(maxSigsPerRound),
			this.maxRounds,
			this.rakeIncreaseDebouncerEpochLength,
			this.minimumStake,
			this.unstakingEpochDelay,
			this.rewardsPerProposal,
			this.minimumCompletedProposalsPercentage,
			this.maxValidators
		);
	}

	public RERulesConfig removeSigsPerRoundLimit() {
		return new RERulesConfig(
			this.reservedSymbols,
			this.feeTable,
			OptionalInt.empty(),
			this.maxRounds,
			this.rakeIncreaseDebouncerEpochLength,
			this.minimumStake,
			this.unstakingEpochDelay,
			this.rewardsPerProposal,
			this.minimumCompletedProposalsPercentage,
			this.maxValidators
		);
	}

	public RERulesConfig overrideFeeTable(FeeTable feeTable) {
		return new RERulesConfig(
			this.reservedSymbols,
			feeTable,
			this.maxSigsPerRound,
			this.maxRounds,
			this.rakeIncreaseDebouncerEpochLength,
			this.minimumStake,
			this.unstakingEpochDelay,
			this.rewardsPerProposal,
			this.minimumCompletedProposalsPercentage,
			this.maxValidators
		);
	}

	public RERulesConfig overrideMaxRounds(long maxRounds) {
		return new RERulesConfig(
			this.reservedSymbols,
			this.feeTable,
			this.maxSigsPerRound,
			maxRounds,
			this.rakeIncreaseDebouncerEpochLength,
			this.minimumStake,
			this.unstakingEpochDelay,
			this.rewardsPerProposal,
			this.minimumCompletedProposalsPercentage,
			this.maxValidators
		);
	}
}
