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

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RERulesVersion;

import java.util.OptionalInt;

public final class StokenetForkConfigsModule extends AbstractModule {
	@ProvidesIntoSet
	ForkConfig stokenet() {
		return new ForkConfig(
			0L,
			"olympia-first-epoch",
			RERulesVersion.OLYMPIA_V1,
			new RERulesConfig(
				FeeTable.create(
					Amount.ofMicroTokens(200), // 0.0002XRD per byte fee
					Amount.ofTokens(1000) // 1000XRD per resource
				),
				OptionalInt.of(50), // 50 Txns per round
				10_000,
				150, // Two weeks worth of epochs
				Amount.ofTokens(100), // Minimum stake
				150, // Two weeks worth of epochs
				Amount.ofTokens(10),   // Rewards per proposal
				9800, // 98.00% threshold for completed proposals to get any rewards,
				100 // 100 max validators
			)
		);
	}
}
