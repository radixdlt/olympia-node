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

package com.radixdlt.application;

import com.google.inject.Inject;
import com.radixdlt.atommodel.tokens.DelegatedStake;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.StateReducer;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Reduces radix engine to stake received
 */
public final class StakeReceivedReducer implements StateReducer<StakeReceived, DelegatedStake> {
	private final ECPublicKey key;

	@Inject
	public StakeReceivedReducer(@Self ECPublicKey key) {
		this.key = Objects.requireNonNull(key);
	}

	@Override
	public Class<StakeReceived> stateClass() {
		return StakeReceived.class;
	}

	@Override
	public Class<DelegatedStake> particleClass() {
		return DelegatedStake.class;
	}

	@Override
	public Supplier<StakeReceived> initial() {
		return StakeReceived::new;
	}

	@Override
	public BiFunction<StakeReceived, DelegatedStake, StakeReceived> outputReducer() {
		return (stakes, p) -> {
			if (p.getDelegateKey().equals(key)) {
				stakes.addStake(p.getOwner(), p.getAmount());
			}
			return stakes;
		};
	}

	@Override
	public BiFunction<StakeReceived, DelegatedStake, StakeReceived> inputReducer() {
		return (stakes, p) -> {
			if (p.getDelegateKey().equals(key)) {
				stakes.removeStake(p.getOwner(), p.getAmount());
			}
			return stakes;
		};
	}
}
