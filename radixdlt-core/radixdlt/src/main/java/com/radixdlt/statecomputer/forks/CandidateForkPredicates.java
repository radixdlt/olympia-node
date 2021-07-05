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

import com.google.common.collect.Streams;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.store.EngineStore;
import com.radixdlt.utils.UInt256;

import java.math.BigDecimal;
import java.math.BigInteger;

public final class CandidateForkPredicates {

	private CandidateForkPredicates() {
	}

	/**
	 * Returns a fork predicate that requires the specified percentage of stake votes.
	 */
	public static CandidateForkPredicate stakeVoting(long minEpoch, int percentage) {
		return new CandidateForkPredicate() {
			@Override
			public long minEpoch() {
				return minEpoch;
			}

			@Override
			public boolean test(
				CandidateForkConfig forkConfig,
				EngineStore<LedgerAndBFTProof> engineStore,
				REParser reParser,
				LedgerAndBFTProof ledgerAndBFTProof
			) {
				final var validatorSet = ledgerAndBFTProof.getProof().getNextValidatorSet().get();

				final var requiredPower = validatorSet.getTotalPower().multiply(UInt256.from(percentage))
					.divide(UInt256.from(10000));

				final var substateDeserialization = reParser.getSubstateDeserialization();

				try (var validatorMetadataCursor = engineStore.openIndexedCursor(
						SubstateIndex.create(SubstateTypeId.VALIDATOR_META_DATA.id(), ValidatorMetaData.class))) {

					final var forkVotesPower = Streams.stream(validatorMetadataCursor)
						.map(s -> {
							try {
								return (ValidatorMetaData) substateDeserialization.deserialize(s.getData());
							} catch (DeserializeException e) {
								throw new IllegalStateException("Failed to deserialize ValidatorMetaData substate");
							}
						})
						.filter(vm -> validatorSet.containsNode(vm.getValidatorKey()))
						.filter(vm -> {
							final var expectedVoteHash = ForkConfig.voteHash(vm.getValidatorKey(), forkConfig);
							return vm.getForkVoteHash().filter(expectedVoteHash::equals).isPresent();
						})
						.map(validatorMetadata -> validatorSet.getPower(validatorMetadata.getValidatorKey()))
						.reduce(UInt256.ZERO, UInt256::add);

					return forkVotesPower.compareTo(requiredPower) >= 0;
				}
			}
		};
	}
}
