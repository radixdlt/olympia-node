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
import com.radixdlt.constraintmachine.AuthorizationException;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;

import java.util.Objects;

public final class AccountBucket implements Bucket {
	private final REAddr resourceAddress;
	private final REAddr holdingAddress;
	// TODO: Remove in mainnet
	private final Long epochUnlocked;

	public AccountBucket(REAddr resourceAddress, REAddr holdingAddress, Long epochUnlocked) {
		this.resourceAddress = resourceAddress;
		this.holdingAddress = holdingAddress;
		this.epochUnlocked = epochUnlocked;
	}

	@Override
	public Authorization withdrawAuthorization() {
		return new Authorization(
			PermissionLevel.USER,
			(r, c) -> {
				try {
					holdingAddress.verifyWithdrawAuthorization(c.key());
				} catch (REAddr.BucketWithdrawAuthorizationException e) {
					throw new AuthorizationException(e.getMessage());
				}
			}
		);
	}

	@Override
	public REAddr resourceAddr() {
		return resourceAddress;
	}

	@Override
	public REAddr getOwner() {
		return holdingAddress;
	}

	@Override
	public ECPublicKey getValidatorKey() {
		return null;
	}

	@Override
	public Long getEpochUnlock() {
		// This should still be null as its a different epoch unlock
		return null;
	}

	@Override
	public String toString() {
		return String.format("%s{res=%s owner=%s}", this.getClass().getSimpleName(), resourceAddress, holdingAddress);
	}

	@Override
	public int hashCode() {
		return Objects.hash(holdingAddress, resourceAddress, epochUnlocked);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AccountBucket)) {
			return false;
		}

		var other = (AccountBucket) o;
		return Objects.equals(this.holdingAddress, other.holdingAddress)
			&& Objects.equals(this.resourceAddress, other.resourceAddress)
			&& Objects.equals(this.epochUnlocked, other.epochUnlocked);
	}
}
