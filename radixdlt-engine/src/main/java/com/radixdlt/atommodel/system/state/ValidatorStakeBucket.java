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

import com.radixdlt.atommodel.tokens.Bucket;
import com.radixdlt.constraintmachine.AuthorizationException;
import com.radixdlt.constraintmachine.ExecutionContext;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.ReadableAddrs;

import java.util.Objects;

public final class ValidatorStakeBucket implements Bucket {
	private final ECPublicKey validatorKey;

	public ValidatorStakeBucket(ECPublicKey validatorKey) {
		this.validatorKey = validatorKey;
	}

	@Override
	public PermissionLevel withdrawPermissionLevel() {
		return PermissionLevel.SUPER_USER;
	}

	@Override
	public void verifyWithdrawAuthorization(ReadableAddrs readable, ExecutionContext context) throws AuthorizationException {
		 // No other authorization required
	}

	@Override
	public REAddr resourceAddr() {
		return REAddr.ofNativeToken();
	}

	@Override
	public REAddr getOwner() {
		return null;
	}

	@Override
	public ECPublicKey getValidatorKey() {
		return validatorKey;
	}

	@Override
	public Long getEpochUnlock() {
		return null;
	}

	@Override
	public int hashCode() {
		return Objects.hash(validatorKey);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ValidatorStakeBucket)) {
			return false;
		}

		var other = (ValidatorStakeBucket) o;
		return Objects.equals(this.validatorKey, other.validatorKey);
	}
}
