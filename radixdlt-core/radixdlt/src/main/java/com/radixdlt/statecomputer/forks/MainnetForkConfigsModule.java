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
import com.radixdlt.atommodel.tokens.Amount;

/**
 * The forks for betanet and the epochs at which they will occur.
 */
public final class MainnetForkConfigsModule extends AbstractModule {
	private static final long TWO_WEEKS_WORTH_OF_ROUNDS = 1_500_000;
	private static final long TWO_WEEKS_WORTH_OF_EPOCHS = 150;

	@ProvidesIntoSet
	ForkConfig olympia() {
		return new ForkConfig(
			0L,
			"olympia",
			RERulesVersion.OLYMPIA_V1,
			new RERulesConfig(
				true,
				TWO_WEEKS_WORTH_OF_ROUNDS,
				TWO_WEEKS_WORTH_OF_EPOCHS,
				Amount.ofTokens(100)
			)
		);
	}
}
