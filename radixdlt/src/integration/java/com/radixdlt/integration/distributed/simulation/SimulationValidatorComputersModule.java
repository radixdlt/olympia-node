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

import java.util.Objects;
import java.util.function.Function;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.statecomputer.RadixEngineStakeComputer;
import com.radixdlt.statecomputer.RadixEngineValidatorsComputer;
import com.radixdlt.statecomputer.RadixEngineValidatorsComputerImpl;
import com.radixdlt.utils.UInt256;

/**
 * Module which manages computers used for validators and stake
 */
public class SimulationValidatorComputersModule extends AbstractModule {
	private final class FixedStakeComputer implements RadixEngineStakeComputer {
		private final UInt256 stake;

		private FixedStakeComputer(UInt256 stake) {
			this.stake = Objects.requireNonNull(stake);
		}

		@Override
		public ImmutableMap<ECPublicKey, UInt256> stakedAmounts(ImmutableSet<ECPublicKey> validators) {
			return validators.stream()
				.collect(ImmutableMap.toImmutableMap(Function.identity(), k -> this.stake));
		}

		@Override
		public RadixEngineStakeComputer removeStake(RadixAddress delegatedAddress, RRI token, UInt256 amount) {
			return this;
		}

		@Override
		public RadixEngineStakeComputer addStake(RadixAddress delegatedAddress, RRI token, UInt256 amount) {
			return this;
		}
	}

	@Override
	protected void configure() {
		bind(RadixEngineValidatorsComputer.class).toProvider(RadixEngineValidatorsComputerImpl::create).in(Scopes.SINGLETON);
		bind(RadixEngineStakeComputer.class).toInstance(new FixedStakeComputer(UInt256.ONE));
	}
}
