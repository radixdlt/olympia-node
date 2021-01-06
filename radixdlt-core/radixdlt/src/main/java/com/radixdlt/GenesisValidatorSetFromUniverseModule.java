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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.GenesisValidatorSetFromUniverse;
import com.radixdlt.consensus.GenesisValidatorSetProvider;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.statecomputer.MaxValidators;
import com.radixdlt.statecomputer.MinValidators;
import com.radixdlt.universe.Universe;

/**
 * Module responsible for supplying a genesis validator set using
 * validator registrations and stakes in the genesis atoms.
 */
public class GenesisValidatorSetFromUniverseModule extends AbstractModule {
	@Provides
	@Singleton
	private GenesisValidatorSetProvider genesisValidatorSetProvider(
		@MinValidators int minValidators,
		@MaxValidators int maxValidators,
		Universe universe,
		@NativeToken RRI nativeToken
	) {
		return new GenesisValidatorSetFromUniverse(minValidators, maxValidators, universe, nativeToken);
	}
}
