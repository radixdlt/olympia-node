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

import com.radixdlt.atommodel.tokens.Fungible;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.nio.ByteBuffer;
import java.util.Objects;

public final class ExittingStake implements Fungible {
	private final UInt256 amount;

	// Bucket keys
	private final REAddr owner;
	private final ECPublicKey delegateKey;
	private final long epochUnlocked;

	public ExittingStake(
		ECPublicKey delegateKey,
		REAddr owner,
		long epochUnlocked,
		UInt256 amount
	) {
		this.delegateKey = Objects.requireNonNull(delegateKey);
		this.owner = Objects.requireNonNull(owner);
		this.amount = Objects.requireNonNull(amount);
		this.epochUnlocked = epochUnlocked;
	}

	public byte[] dataKey() {
		var dataSize = ECPublicKey.COMPRESSED_BYTES + (ECPublicKey.COMPRESSED_BYTES + 1) + Long.BYTES;
		var bytes = new byte[dataSize];
		var byteBuffer = ByteBuffer.wrap(bytes);
		byteBuffer.putLong(epochUnlocked);
		byteBuffer.put(delegateKey.getCompressedBytes());
		byteBuffer.put(owner.getBytes());
		return bytes;
	}

	public long getEpochUnlocked() {
		return epochUnlocked;
	}

	public TokensInAccount unlock() {
		return new TokensInAccount(
			owner, amount, REAddr.ofNativeToken()
		);
	}

	public Bucket resourceInBucket() {
		return new ExittingStakeBucket(owner, delegateKey, epochUnlocked);
	}

	public ECPublicKey getDelegateKey() {
		return delegateKey;
	}

	public REAddr getOwner() {
		return this.owner;
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s:%s:%s]",
			getClass().getSimpleName(),
			amount,
			owner,
			delegateKey,
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
		if (!(o instanceof ExittingStake)) {
			return false;
		}
		var that = (ExittingStake) o;
		return Objects.equals(delegateKey, that.delegateKey)
			&& Objects.equals(owner, that.owner)
			&& Objects.equals(amount, that.amount)
			&& this.epochUnlocked == that.epochUnlocked;
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			delegateKey,
			owner,
			amount,
			epochUnlocked
		);
	}
}
