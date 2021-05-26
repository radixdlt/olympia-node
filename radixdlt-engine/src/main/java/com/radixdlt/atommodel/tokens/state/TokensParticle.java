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

import com.radixdlt.atommodel.system.state.HasEpochData;
import com.radixdlt.atommodel.tokens.Fungible;
import com.radixdlt.constraintmachine.AuthorizationException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.ReadableAddrs;
import com.radixdlt.utils.UInt256;
import java.util.Objects;
import java.util.Optional;

/**
 *  A particle which represents an amount of transferrable fungible tokens
 *  owned by some key owner and stored in an account.
 */
public final class TokensParticle implements Fungible {
	private final UInt256 amount;

	private final REAddr resourceAddr;

	// Bucket
	private final REAddr holdingAddress;
	// Bucket properties
	private final Long epochUnlocked;

	public TokensParticle(
		REAddr holdingAddress,
		UInt256 amount,
		REAddr resourceAddr
	) {
		this.holdingAddress = Objects.requireNonNull(holdingAddress);
		this.resourceAddr = Objects.requireNonNull(resourceAddr);
		this.amount = Objects.requireNonNull(amount);
		this.epochUnlocked = null;
	}

	public TokensParticle(
		REAddr holdingAddress,
		UInt256 amount,
		REAddr resourceAddr,
		Long epochUnlocked
	) {
		this.holdingAddress = Objects.requireNonNull(holdingAddress);
		this.resourceAddr = Objects.requireNonNull(resourceAddr);
		this.amount = Objects.requireNonNull(amount);
		this.epochUnlocked = epochUnlocked;
	}

	public void verifyWithdrawAuthorization(Optional<ECPublicKey> key, ReadableAddrs readable) throws AuthorizationException {
		try {
			holdingAddress.verifyWithdrawAuthorization(key);
		} catch (REAddr.BucketWithdrawAuthorizationException e) {
			throw new AuthorizationException(e.getMessage());
		}

		if (epochUnlocked != null) {
			var system = (HasEpochData) readable.loadAddr(null, REAddr.ofSystem()).orElseThrow();
			if (epochUnlocked > system.getEpoch()) {
				throw new AuthorizationException("Tokens are locked until epoch " + epochUnlocked + " current " + system.getEpoch());
			}
		}
	}

	public ResourceInBucket resourceInBucket() {
		return new ResourceInBucket(resourceAddr, holdingAddress, epochUnlocked);
	}

	public Optional<Long> getEpochUnlocked() {
		return Optional.ofNullable(epochUnlocked);
	}

	public REAddr getHoldingAddr() {
		return this.holdingAddress;
	}

	public REAddr getResourceAddr() {
		return this.resourceAddr;
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s:%s:%s]",
			getClass().getSimpleName(),
			resourceAddr,
			amount,
			holdingAddress,
			epochUnlocked
		);
	}

	@Override
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
			&& Objects.equals(resourceAddr, that.resourceAddr)
			&& Objects.equals(epochUnlocked, that.epochUnlocked)
			&& Objects.equals(amount, that.amount);
	}

	@Override
	public int hashCode() {
		return Objects.hash(holdingAddress, resourceAddr, epochUnlocked, amount);
	}
}
