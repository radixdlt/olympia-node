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

package com.radixdlt.atommodel.system.state;

import com.radixdlt.atommodel.tokens.ResourceInBucket;
import com.radixdlt.atommodel.tokens.Bucket;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

public final class StakeOwnership implements ResourceInBucket {
	private final UInt256 amount;

	// Bucket keys
	private final REAddr owner;
	private final ECPublicKey delegateKey;

	public StakeOwnership(
		ECPublicKey delegateKey,
		REAddr owner,
		UInt256 amount
	) {
		if (amount.isZero()) {
			throw new IllegalArgumentException("Stake ownership should not be zero");
		}
		this.delegateKey = Objects.requireNonNull(delegateKey);
		this.owner = Objects.requireNonNull(owner);
		this.amount = Objects.requireNonNull(amount);
	}

	@Override
	public UInt256 getAmount() {
		return this.amount;
	}

	public Bucket bucket() {
		return new StakeOwnershipBucket(delegateKey, owner);
	}

	public ECPublicKey getDelegateKey() {
		return delegateKey;
	}

	public REAddr getOwner() {
		return this.owner;
	}

	@Override
	public String toString() {
		return String.format("%s{delegate=%s owner=%s amt=%s}",
			getClass().getSimpleName(),
			delegateKey,
			owner,
			amount
		);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof StakeOwnership)) {
			return false;
		}
		StakeOwnership that = (StakeOwnership) o;
		return Objects.equals(delegateKey, that.delegateKey)
			&& Objects.equals(owner, that.owner)
			&& Objects.equals(amount, that.amount);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			delegateKey,
			owner,
			amount
		);
	}
}
