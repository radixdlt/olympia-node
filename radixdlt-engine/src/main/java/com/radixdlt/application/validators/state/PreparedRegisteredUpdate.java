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

package com.radixdlt.application.validators.state;

import com.google.common.hash.HashCode;
import com.radixdlt.application.validators.scrypt.ValidatorData;
import com.radixdlt.crypto.ECPublicKey;

import java.util.Objects;
import java.util.Optional;

public final class PreparedRegisteredUpdate implements ValidatorData {
	private final ECPublicKey validatorKey;
	private final boolean isRegistered;
	private final Optional<HashCode> forkVoteHash;

	public PreparedRegisteredUpdate(ECPublicKey validatorKey, boolean isRegistered, Optional<HashCode> forkVoteHash) {
		this.validatorKey = validatorKey;
		this.isRegistered = isRegistered;
		this.forkVoteHash = forkVoteHash;
	}

	@Override
	public ECPublicKey getValidatorKey() {
		return validatorKey;
	}

	public boolean isRegistered() {
		return isRegistered;
	}

	public Optional<HashCode> getForkVoteHash() {
		return forkVoteHash;
	}

	@Override
	public int hashCode() {
		return Objects.hash(validatorKey, isRegistered, forkVoteHash);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof PreparedRegisteredUpdate)) {
			return false;
		}

		var other = (PreparedRegisteredUpdate) o;
		return Objects.equals(this.validatorKey, other.validatorKey)
			&& this.isRegistered == other.isRegistered
			&& Objects.equals(this.forkVoteHash, other.forkVoteHash);
	}
}
