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
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;

import java.util.Objects;

public final class ExittingOwnershipBucket implements Bucket {
	private final REAddr owner;
	private final ECPublicKey delegate;

	public ExittingOwnershipBucket(REAddr owner, ECPublicKey delegate) {
		this.owner = owner;
		this.delegate = delegate;
	}

	@Override
	public Authorization withdrawAuthorization() {
		return new Authorization(PermissionLevel.SUPER_USER, (r, c) -> { });
	}

	@Override
	public REAddr resourceAddr() {
		return null;
	}

	@Override
	public REAddr getOwner() {
		return owner;
	}

	@Override
	public ECPublicKey getValidatorKey() {
		return delegate;
	}

	@Override
	public Long getEpochUnlock() {
		return 0L;
	}

	@Override
	public int hashCode() {
		return Objects.hash(owner, delegate);
	}

	public boolean equals(Object o) {
		if (!(o instanceof ExittingOwnershipBucket)) {
			return false;
		}

		var other = (ExittingOwnershipBucket) o;
		return Objects.equals(this.owner, other.owner)
			&& Objects.equals(this.delegate, other.delegate);
	}
}
