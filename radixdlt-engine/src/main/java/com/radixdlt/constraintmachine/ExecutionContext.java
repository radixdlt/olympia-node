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

package com.radixdlt.constraintmachine;

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;

import java.util.Optional;

public final class ExecutionContext {
	private final PermissionLevel level;
	private final Optional<ECPublicKey> key;
	private UInt256 feeReserve;

	public ExecutionContext(PermissionLevel level, Optional<ECPublicKey> key) {
		this.level = level;
		this.key = key;
		this.feeReserve = UInt256.ZERO;
	}

	public void depositFeeReserve(UInt256 fee) {
		this.feeReserve = this.feeReserve.add(fee);
	}

	public void verifyHasReserve(UInt256 amount) throws NotEnoughFeesException {
		if (feeReserve.compareTo(amount) < 0) {
			throw new NotEnoughFeesException();
		}
	}

	public UInt256 feeReserve() {
		return this.feeReserve;
	}

	public Optional<ECPublicKey> key() {
		return key;
	}

	public PermissionLevel permissionLevel() {
		return level;
	}

	public void verifyPermissionLevel(PermissionLevel requiredLevel) throws ConstraintMachineException {
		if (this.level.compareTo(requiredLevel) < 0) {
			throw new ConstraintMachineException(
				CMErrorCode.PERMISSION_LEVEL_ERROR,
				"Required: " + requiredLevel + " Current: " + this.level
			);
		}

		if (requiredLevel.compareTo(PermissionLevel.SUPER_USER) >= 0 && key.isPresent()) {
			throw new ConstraintMachineException(
				CMErrorCode.AUTHORIZATION_ERROR,
				"System updates should not be signed."
			);
		}
	}
}
