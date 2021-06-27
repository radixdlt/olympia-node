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

import com.radixdlt.atommodel.tokens.scrypt.Tokens;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.exceptions.InvalidPermissionException;
import com.radixdlt.constraintmachine.exceptions.InvalidResourceException;
import com.radixdlt.constraintmachine.exceptions.NotEnoughFeesException;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.constraintmachine.exceptions.SignedSystemException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.Optional;

public final class ExecutionContext {
	private final PermissionLevel level;
	private ECPublicKey key;
	private boolean disableResourceAllocAndDestroy;
	private UInt256 feeReserve;
	private int sigsLeft;

	public ExecutionContext(
		PermissionLevel level,
		UInt256 feeReserve,
		int sigsLeft
	) {
		this.level = level;
		this.feeReserve = feeReserve;
		this.sigsLeft = sigsLeft;
	}

	public void resetSigs(int sigs) {
		this.sigsLeft = sigs;
	}

	public void sig() throws AuthorizationException {
		if (this.sigsLeft == 0) {
			throw new AuthorizationException("Used up all signatures allowed");
		}
		this.sigsLeft--;
	}

	public int sigsLeft() {
		return sigsLeft;
	}

	public void depositFeeReserve(Tokens tokens) throws InvalidResourceException {
		if (!tokens.getResourceAddr().isNativeToken()) {
			throw new InvalidResourceException(REAddr.ofNativeToken(), tokens.getResourceAddr());
		}

		this.feeReserve = this.feeReserve.add(tokens.getAmount().getLow());
	}

	public void verifyHasReserve(UInt256 amount) throws NotEnoughFeesException {
		if (feeReserve.compareTo(amount) < 0) {
			throw new NotEnoughFeesException();
		}
	}

	public void verifyCanAllocAndDestroyResources() throws ProcedureException {
		if (disableResourceAllocAndDestroy) {
			throw new ProcedureException("Destruction of resources not enabled.");
		}
	}

	public void setDisableResourceAllocAndDestroy(boolean disableResourceAllocAndDestroy) {
		this.disableResourceAllocAndDestroy = disableResourceAllocAndDestroy;
	}

	public void setKey(ECPublicKey key) {
		this.key = key;
	}

	public Optional<ECPublicKey> key() {
		return Optional.ofNullable(key);
	}

	public PermissionLevel permissionLevel() {
		return level;
	}

	public void verifyPermissionLevel(PermissionLevel requiredLevel) throws SignedSystemException, InvalidPermissionException {
		if (this.level.compareTo(requiredLevel) < 0) {
			throw new InvalidPermissionException(requiredLevel, level);
		}

		if (requiredLevel.compareTo(PermissionLevel.SUPER_USER) >= 0 && key != null) {
			throw new SignedSystemException();
		}
	}
}
