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

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;

import java.util.Objects;

public class ExittingStakeBucket implements Bucket {
	private final REAddr owner;
	private final ECPublicKey delegateKey;

	public ExittingStakeBucket(REAddr owner, ECPublicKey delegateKey) {
		this.owner = owner;
		this.delegateKey = delegateKey;
	}

	@Override
	public REAddr resourceAddr() {
		return REAddr.ofNativeToken();
	}

	@Override
	public REAddr getOwner() {
		return owner;
	}

	@Override
	public ECPublicKey getValidatorKey() {
		return delegateKey;
	}

	@Override
	public Long getEpochUnlock() {
		return null;
	}

	@Override
	public int hashCode() {
		return Objects.hash(owner, delegateKey);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ExittingStakeBucket)) {
			return false;
		}

		var other = (ExittingStakeBucket) o;
		return Objects.equals(this.owner, other.owner)
			&& Objects.equals(this.delegateKey, other.delegateKey);
	}
}