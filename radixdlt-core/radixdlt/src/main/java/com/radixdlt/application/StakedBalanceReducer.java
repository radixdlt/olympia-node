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
import com.radixdlt.engine.StateReducer;
import com.radixdlt.identifiers.REAddr;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class StakedBalanceReducer implements StateReducer<StakedBalance, DelegatedStake> {
	private final REAddr accountAddr;

	@Inject
	public StakedBalanceReducer(@Self REAddr accountAddr) {
		this.accountAddr = Objects.requireNonNull(accountAddr);
	}

	@Override
	public Class<StakedBalance> stateClass() {
		return StakedBalance.class;
	}

	@Override
	public Class<DelegatedStake> particleClass() {
		return DelegatedStake.class;
	}

	@Override
	public Supplier<StakedBalance> initial() {
		return StakedBalance::new;
	}

	@Override
	public BiFunction<StakedBalance, DelegatedStake, StakedBalance> outputReducer() {
		return (stakes, p) -> {
			if (p.getOwner().equals(accountAddr)) {
				stakes.addStake(p.getDelegateKey(), p.getAmount());
			}
			return stakes;
		};
	}

	@Override
	public BiFunction<StakedBalance, DelegatedStake, StakedBalance> inputReducer() {
		return (balance, p) -> {
			if (p.getOwner().equals(accountAddr)) {
				balance.removeStake(p.getDelegateKey(), p.getAmount());
			}
			return balance;
		};
	}
}
