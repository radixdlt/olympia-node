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

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.ValidatorRakeCopy;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;

import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

public final class StokenetForkConfigsModule extends AbstractModule {
	@ProvidesIntoSet
	ForkConfig stokenet() {
		return new ForkConfig(
			0L,
			"olympia-first-epoch",
			RERulesVersion.OLYMPIA_V1,
			new RERulesConfig(
				Set.of("xrd"),
				FeeTable.create(
					Amount.ofMicroTokens(200), // 0.0002XRD per byte fee
					Map.of(
						TokenResource.class, Amount.ofTokens(1000), // 1000XRD per resource
						ValidatorRegisteredCopy.class, Amount.ofTokens(5), // 5XRD per validator update
						ValidatorRakeCopy.class, Amount.ofTokens(5), // 5XRD per register update
						ValidatorOwnerCopy.class, Amount.ofTokens(5), // 5XRD per register update
						ValidatorMetaData.class, Amount.ofTokens(5), // 5XRD per register update
						AllowDelegationFlag.class, Amount.ofTokens(5), // 5XRD per register update
						PreparedStake.class, Amount.ofMilliTokens(500), // 0.5XRD per stake
						PreparedUnstakeOwnership.class, Amount.ofMilliTokens(500) // 0.5XRD per unstake
					)
				),
				OptionalInt.of(50), // 50 Txns per round
				10_000,
				2,
				Amount.ofTokens(100), // Minimum stake
				1, // Two weeks worth of epochs
				Amount.ofTokens(10),   // Rewards per proposal
				9800, // 98.00% threshold for completed proposals to get any rewards,
				100 // 100 max validators
			)
		);
	}
}
