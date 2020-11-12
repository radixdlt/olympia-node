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

package com.radixdlt;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.statecomputer.RadixEngineStakeComputer;
import com.radixdlt.statecomputer.RadixEngineStakeComputerImpl;
import com.radixdlt.statecomputer.RadixEngineValidatorsComputer;
import com.radixdlt.statecomputer.RadixEngineValidatorsComputerImpl;
import com.radixdlt.utils.UInt256;

/**
 * Module which manages computers used for validators and stake
 */
public class RadixEngineValidatorComputersModule extends AbstractModule {
	@Override
	protected void configure() {
		// Nothing to do
	}

	@Provides
	private RadixEngineValidatorsComputer validatorsComputer(
		BFTValidatorSet initialValidatorSet
	) {
		ImmutableSet<ECPublicKey> initialValidatorKeys = initialValidatorSet.getValidators().stream()
			.map(v -> v.getNode().getKey())
			.collect(ImmutableSet.toImmutableSet());

		return RadixEngineValidatorsComputerImpl.create(initialValidatorKeys);
	}

	@Provides
	private RadixEngineStakeComputer stakeComputer(
		@NativeToken RRI feeToken, // FIXME: ability to use a different token for fees and staking
		BFTValidatorSet initialValidatorSet
	) {
		ImmutableMap<ECPublicKey, UInt256> initialAmounts = initialValidatorSet.getValidators().stream()
				.collect(ImmutableMap.toImmutableMap(v -> v.getNode().getKey(), BFTValidator::getPower));

		return RadixEngineStakeComputerImpl.create(feeToken, initialAmounts);
	}
}
