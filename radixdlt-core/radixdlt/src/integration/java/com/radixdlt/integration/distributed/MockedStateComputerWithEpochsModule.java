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

package com.radixdlt.integration.distributed;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.statecomputer.EpochCeilingView;

import java.util.function.Function;

public class MockedStateComputerWithEpochsModule extends AbstractModule {
	@Provides
	@Singleton
	private StateComputer stateComputer(
		@EpochCeilingView View epochHighView,
		Function<Long, BFTValidatorSet> validatorSetMapping,
		Hasher hasher
	) {
		return new MockedStateComputerWithEpochs(hasher, validatorSetMapping, epochHighView);
	}
}
