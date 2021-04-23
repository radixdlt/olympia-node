/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.atommodel.tokens;

import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import java.util.Objects;

/**
 *  A particle which represents an amount of transferrable fungible tokens
 *  owned by some key owner and stored in an account.
 */
public final class TokensParticle implements Particle {
	private final REAddr tokenAddress;
	private final REAddr holdingAddress;
	private final UInt256 amount;

	public TokensParticle(
		REAddr holdingAddress,
		UInt256 amount,
		REAddr tokenAddress
	) {
		this.holdingAddress = Objects.requireNonNull(holdingAddress);
		this.tokenAddress = Objects.requireNonNull(tokenAddress);
		this.amount = Objects.requireNonNull(amount);
	}

	public REAddr getHoldingAddr() {
		return this.holdingAddress;
	}

	public REAddr getRri() {
		return this.tokenAddress;
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s:%s]",
			getClass().getSimpleName(),
			tokenAddress,
			amount,
			holdingAddress
		);
	}

	public UInt256 getAmount() {
		return this.amount;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof TokensParticle)) {
			return false;
		}
		TokensParticle that = (TokensParticle) o;
		return Objects.equals(holdingAddress, that.holdingAddress)
			&& Objects.equals(tokenAddress, that.tokenAddress)
			&& Objects.equals(amount, that.amount);
	}

	@Override
	public int hashCode() {
		return Objects.hash(holdingAddress, tokenAddress, amount);
	}
}
