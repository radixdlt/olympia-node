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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.statecomputer.RegisteredValidators;
import com.radixdlt.statecomputer.Stakes;
import com.radixdlt.utils.UInt256;

/**
 * Module which manages computers used for validators and stake
 */
public class SimulationValidatorComputersModule extends AbstractModule {
	@Provides
	private Stakes stakes(ImmutableList<BFTValidator> allValidators) {
		ImmutableMap<ECPublicKey, UInt256> stakes = allValidators.stream()
			.filter(validator -> !validator.getPower().isZero())
			.collect(ImmutableMap.toImmutableMap(v -> v.getNode().getKey(), v -> v.getPower()));
		return Stakes.create(stakes);
	}

	@Provides
    @Singleton
	private RegisteredValidators validators(BFTValidatorSet initialValidatorSet) {
		return initialValidatorSet.getValidators().stream()
			.map(BFTValidator::getNode)
			.map(BFTNode::getKey)
			.reduce(RegisteredValidators.create(), RegisteredValidators::add, RegisteredValidators::combine);
	}
}
