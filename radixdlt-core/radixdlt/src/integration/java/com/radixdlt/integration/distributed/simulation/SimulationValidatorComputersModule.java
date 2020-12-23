/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.integration.distributed.simulation;

import java.util.Collection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.statecomputer.RadixEngineStakeComputer;
import com.radixdlt.statecomputer.RadixEngineValidatorsComputer;
import com.radixdlt.statecomputer.RadixEngineValidatorsComputerImpl;
import com.radixdlt.statecomputer.TokenStakeComputer;
import com.radixdlt.utils.UInt256;

/**
 * Module which manages computers used for validators and stake
 */
public class SimulationValidatorComputersModule extends AbstractModule {
	private final class SimulationStakeComputer implements RadixEngineStakeComputer {
		private final RadixEngineStakeComputer delegate;

		private SimulationStakeComputer(RRI stakingToken, ImmutableMap<ECPublicKey, UInt256> stakes) {
			var initial = TokenStakeComputer.create(stakingToken);
			for (final var stake : stakes.entrySet()) {
				initial = initial.addStake(stake.getKey(), stakingToken, stake.getValue());
			}
			this.delegate = initial;
		}

		@Override
		public RadixEngineStakeComputer removeStake(ECPublicKey delegatedKey, RRI token, UInt256 amount) {
			return this.delegate.removeStake(delegatedKey, token, amount);
		}

		@Override
		public RadixEngineStakeComputer addStake(ECPublicKey delegatedKey, RRI token, UInt256 amount) {
			return this.delegate.addStake(delegatedKey, token, amount);
		}

		@Override
		public ImmutableMap<ECPublicKey, UInt256> stakedAmounts(ImmutableSet<ECPublicKey> validators) {
			return this.delegate.stakedAmounts(validators);
		}
	}

	private final class SimulationValidatorComputer implements RadixEngineValidatorsComputer {
		private final RadixEngineValidatorsComputer delegate;

		private SimulationValidatorComputer(Collection<ECPublicKey> initialValidators) {
			var initialComputer = RadixEngineValidatorsComputerImpl.create();
			for (final var key : initialValidators) {
				initialComputer = initialComputer.addValidator(key);
			}
			this.delegate = initialComputer;
		}

		@Override
		public RadixEngineValidatorsComputer addValidator(ECPublicKey validatorKey) {
			return this.delegate.addValidator(validatorKey);
		}

		@Override
		public RadixEngineValidatorsComputer removeValidator(ECPublicKey validatorKey) {
			return this.delegate.removeValidator(validatorKey);
		}

		@Override
		public ImmutableSet<ECPublicKey> activeValidators() {
			return this.delegate.activeValidators();
		}
	}

	@Provides
	private RadixEngineStakeComputer stakeComputer(@NativeToken RRI nativeToken, ImmutableList<BFTValidator> allValidators) {
		ImmutableMap<ECPublicKey, UInt256> stakes = allValidators.stream()
			.filter(validator -> !validator.getPower().isZero())
			.collect(ImmutableMap.toImmutableMap(v -> v.getNode().getKey(), v -> v.getPower()));
		return new SimulationStakeComputer(nativeToken, stakes);
	}

	@Provides
	private RadixEngineValidatorsComputer validatorComputer(BFTValidatorSet initialValidatorSet) {
		ImmutableList<ECPublicKey> validatorKeys = initialValidatorSet.getValidators().stream()
			.map(BFTValidator::getNode)
			.map(BFTNode::getKey)
			.collect(ImmutableList.toImmutableList());
		return new SimulationValidatorComputer(validatorKeys);
	}
}
