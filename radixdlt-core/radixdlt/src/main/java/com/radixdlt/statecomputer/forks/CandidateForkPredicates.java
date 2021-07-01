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

import com.radixdlt.engine.RadixEngine;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.StakedValidators;
import com.radixdlt.utils.UInt256;

import java.math.BigDecimal;
import java.math.BigInteger;

public final class CandidateForkPredicates {

	private CandidateForkPredicates() {
	}

	/**
	 * Returns a fork predicate that requires the specified percentage of stake votes.
	 */
	public static CandidateForkPredicate stakeVoting(long minEpoch, double requiredPercentage) {
		return new CandidateForkPredicate() {
			@Override
			public long minEpoch() {
				return minEpoch;
			}

			@Override
			public boolean test(
				CandidateForkConfig forkConfig,
				RadixEngine<LedgerAndBFTProof> radixEngine,
				LedgerAndBFTProof uncommittedProof
			) {
				final var validatorSet = uncommittedProof.getProof().getNextValidatorSet().get();
				final var stakedValidators = radixEngine.getComputedState(StakedValidators.class);
				final var forksVotes = stakedValidators.getForksVotes();

				final var requiredPower =
					new BigDecimal(new BigInteger(1, validatorSet.getTotalPower().toByteArray()))
						.multiply(BigDecimal.valueOf(requiredPercentage));

				final var forkVotesPower = validatorSet.getValidators().stream()
					.map(validator -> {
						final var key = validator.getNode().getKey();
						final var expectedVoteHash = ForkConfig.voteHash(key, forkConfig);
						if (forksVotes.containsKey(key) && forksVotes.get(key).equals(expectedVoteHash)) {
							return validator.getPower();
						} else {
							return UInt256.ZERO;
						}
					}).reduce(UInt256.ZERO, UInt256::add);

				return new BigDecimal(new BigInteger(1, forkVotesPower.toByteArray()))
					.compareTo(requiredPower) >= 0;
			}

		};
	}
}
