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
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.engine.StateReducer;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class Balance implements StateReducer<UInt256, TransferrableTokensParticle> {
	private final RRI tokenRRI;
	private final RadixAddress address;

	@Inject
	public Balance(
		@NativeToken RRI tokenRRI,
		@Self RadixAddress address
	) {
		this.tokenRRI = Objects.requireNonNull(tokenRRI);
		this.address = Objects.requireNonNull(address);
	}

	@Override
	public Class<UInt256> stateClass() {
		return UInt256.class;
	}

	@Override
	public Class<TransferrableTokensParticle> particleClass() {
		return TransferrableTokensParticle.class;
	}

	@Override
	public Supplier<UInt256> initial() {
		return () -> UInt256.ZERO;
	}

	@Override
	public BiFunction<UInt256, TransferrableTokensParticle, UInt256> outputReducer() {
		return (balance, p) -> {
			if (p.getAddress().equals(address) && p.getTokDefRef().equals(tokenRRI)) {
				return balance.add(p.getAmount());
			}
			return balance;
		};
	}

	@Override
	public BiFunction<UInt256, TransferrableTokensParticle, UInt256> inputReducer() {
		return (balance, p) -> {
			if (p.getAddress().equals(address) && p.getTokDefRef().equals(tokenRRI)) {
				return balance.add(p.getAmount());
			}
			return balance;
		};
	}
}
