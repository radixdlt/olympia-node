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

package com.radixdlt.atommodel.tokens.state;

import com.radixdlt.atommodel.tokens.Bucket;
import com.radixdlt.atommodel.tokens.ResourceInBucket;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import java.util.Objects;

/**
 *  A particle which represents an amount of transferrable fungible tokens
 *  owned by some key owner and stored in an account.
 */
public final class TokensInAccount implements ResourceInBucket {
	private final UInt256 amount;

	private final REAddr resourceAddr;

	// Bucket
	private final REAddr holdingAddress;

	public TokensInAccount(
		REAddr holdingAddress,
		UInt256 amount,
		REAddr resourceAddr
	) {
		this.holdingAddress = Objects.requireNonNull(holdingAddress);
		this.resourceAddr = Objects.requireNonNull(resourceAddr);
		this.amount = Objects.requireNonNull(amount);
	}

	@Override
	public UInt256 getAmount() {
		return this.amount;
	}

	@Override
	public Bucket bucket() {
		return new AccountBucket(resourceAddr, holdingAddress);
	}

	public REAddr getHoldingAddr() {
		return this.holdingAddress;
	}

	public REAddr getResourceAddr() {
		return this.resourceAddr;
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s:%s]",
			getClass().getSimpleName(),
			resourceAddr,
			amount,
			holdingAddress
		);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof TokensInAccount)) {
			return false;
		}
		TokensInAccount that = (TokensInAccount) o;
		return Objects.equals(holdingAddress, that.holdingAddress)
			&& Objects.equals(resourceAddr, that.resourceAddr)
			&& Objects.equals(amount, that.amount);
	}

	@Override
	public int hashCode() {
		return Objects.hash(holdingAddress, resourceAddr, amount);
	}
}
