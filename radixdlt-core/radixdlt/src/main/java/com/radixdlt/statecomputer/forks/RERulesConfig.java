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

import com.radixdlt.atommodel.tokens.Amount;

public final class RERulesConfig {
	private final long maxRounds;
	private final boolean fees;
	private final long rakeIncreaseDebouncerEpochLength;
	private final Amount minimumStake;
	private final Amount rewardsPerProposal;

	public RERulesConfig(
		boolean fees,
		long maxRounds,
		long rakeIncreaseDebouncerEpochLength,
		Amount minimumStake,
		Amount rewardsPerProposal
	) {
		this.fees = fees;
		this.maxRounds = maxRounds;
		this.rakeIncreaseDebouncerEpochLength = rakeIncreaseDebouncerEpochLength;
		this.minimumStake = minimumStake;
		this.rewardsPerProposal = rewardsPerProposal;
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

	public RERulesConfig overrideMaxRounds(long maxRounds) {
		return new RERulesConfig(this.fees, maxRounds, this.rakeIncreaseDebouncerEpochLength, this.minimumStake, this.rewardsPerProposal);
	}
}
