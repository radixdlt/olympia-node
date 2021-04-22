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
import com.radixdlt.atommodel.tokens.TokensParticle;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.StateReducer;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Balance reducer for local node
 */
public final class BalanceReducer implements StateReducer<Balances, TokensParticle> {
	private final ECPublicKey key;

	@Inject
	public BalanceReducer(@Self ECPublicKey key) {
		this.key = Objects.requireNonNull(key);
	}

	@Override
	public Class<Balances> stateClass() {
		return Balances.class;
	}

	@Override
	public Class<TokensParticle> particleClass() {
		return TokensParticle.class;
	}

	@Override
	public Supplier<Balances> initial() {
		return Balances::new;
	}

	@Override
	public BiFunction<Balances, TokensParticle, Balances> outputReducer() {
		return (balance, p) -> {
			if (p.getAddress().equals(key)) {
				return balance.add(p.getRri(), p.getAmount());
			}
			return balance;
		};
	}

	@Override
	public BiFunction<Balances, TokensParticle, Balances> inputReducer() {
		return (balance, p) -> {
			if (p.getAddress().equals(key)) {
				return balance.remove(p.getRri(), p.getAmount());
			}
			return balance;
		};
	}
}
