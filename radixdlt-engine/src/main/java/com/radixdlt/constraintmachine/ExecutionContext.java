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

import com.radixdlt.atom.Txn;
import com.radixdlt.application.tokens.scrypt.Tokens;
import com.radixdlt.application.tokens.scrypt.TokenHoldingBucket;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.exceptions.DefaultedSystemLoanException;
import com.radixdlt.constraintmachine.exceptions.ExecutionContextDestroyException;
import com.radixdlt.constraintmachine.exceptions.InvalidPermissionException;
import com.radixdlt.constraintmachine.exceptions.InvalidResourceException;
import com.radixdlt.constraintmachine.exceptions.DepletedFeeReserveException;
import com.radixdlt.constraintmachine.exceptions.MultipleFeeReserveDepositException;
import com.radixdlt.constraintmachine.exceptions.NotEnoughResourcesException;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.constraintmachine.exceptions.SignedSystemException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

// TODO: Cleanup permissions to access to these methods
public final class ExecutionContext {
	private final Txn txn;
	private final PermissionLevel level;
	private final TokenHoldingBucket reserve;
	private ECPublicKey key;
	private boolean disableResourceAllocAndDestroy;
	private UInt256 feeDeposit;
	private UInt256 systemLoan;
	private int sigsLeft;
	private boolean chargedOneTimeFee = false;
	private List<REEvent> events = new ArrayList<>();

	public ExecutionContext(
		Txn txn,
		PermissionLevel level,
		int sigsLeft,
		UInt256 systemLoan
	) {
		this.txn = txn;
		this.level = level;
		this.sigsLeft = sigsLeft;
		this.systemLoan = systemLoan;
		this.reserve = new TokenHoldingBucket(Tokens.create(REAddr.ofNativeToken(), systemLoan));
	}

	public List<REEvent> getEvents() {
		return events;
	}

	public void emitEvent(REEvent event) {
		this.events.add(event);
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

	public Tokens withdrawFeeReserve(UInt256 amount) throws InvalidResourceException, NotEnoughResourcesException {
		return reserve.withdraw(REAddr.ofNativeToken(), amount);
	}

	public void depositFeeReserve(Tokens tokens) throws InvalidResourceException, MultipleFeeReserveDepositException {
		if (feeDeposit != null) {
			throw new MultipleFeeReserveDepositException();
		}
		reserve.deposit(tokens);
		feeDeposit = tokens.getAmount().getLow();
	}

	public void chargeOneTimeTransactionFee(Function<Txn, UInt256> feeComputer) throws DepletedFeeReserveException {
		if (chargedOneTimeFee) {
			return;
		}

		var fee = feeComputer.apply(txn);
		charge(fee);
		chargedOneTimeFee = true;
	}

	public void charge(UInt256 amount) throws DepletedFeeReserveException {
		try {
			reserve.withdraw(REAddr.ofNativeToken(), amount);
		} catch (InvalidResourceException e) {
			throw new IllegalStateException("Should not get here", e);
		} catch (NotEnoughResourcesException e) {
			throw new DepletedFeeReserveException(e);
		}
	}

	public void payOffLoan() throws DefaultedSystemLoanException {
		if (systemLoan.isZero()) {
			return;
		}

		try {
			charge(systemLoan);
		} catch (DepletedFeeReserveException e) {
			throw new DefaultedSystemLoanException(e, feeDeposit);
		}
		systemLoan = UInt256.ZERO;
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

	public void destroy() throws DefaultedSystemLoanException, ExecutionContextDestroyException {
		payOffLoan();

		if (!reserve.isEmpty()) {
			throw new ExecutionContextDestroyException(reserve);
		}
	}
}
