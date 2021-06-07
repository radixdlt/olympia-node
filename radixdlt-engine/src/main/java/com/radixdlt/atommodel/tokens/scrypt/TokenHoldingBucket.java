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

package com.radixdlt.atommodel.tokens.scrypt;

import com.radixdlt.atommodel.tokens.state.TokenResource;
import com.radixdlt.constraintmachine.InvalidResourceException;
import com.radixdlt.constraintmachine.NotEnoughResourcesException;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.ReadableAddrs;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

public class TokenHoldingBucket implements ReducerState {
	private final REAddr resourceAddr;
	private final UInt384 amount;

	TokenHoldingBucket(
		REAddr resourceAddr,
		UInt384 amount
	) {
		this.resourceAddr = resourceAddr;
		this.amount = amount;
	}

	public boolean isEmpty() {
		return amount.isZero();
	}

	public REAddr getResourceAddr() {
		return resourceAddr;
	}

	public TokenHoldingBucket deposit(REAddr resourceAddr, UInt256 amountToAdd) throws ProcedureException {
		if (!this.resourceAddr.equals(resourceAddr)) {
			throw new InvalidResourceException(resourceAddr, this.resourceAddr);
		}

		return new TokenHoldingBucket(this.resourceAddr, UInt384.from(amountToAdd).add(amount));
	}

	public TokenHoldingBucket withdraw(REAddr resourceAddr, UInt256 amountToWithdraw) throws ProcedureException {
		if (!this.resourceAddr.equals(resourceAddr)) {
			throw new InvalidResourceException(resourceAddr, this.resourceAddr);
		}

		var withdraw384 = UInt384.from(amountToWithdraw);
		if (amount.compareTo(withdraw384) < 0) {
			throw new NotEnoughResourcesException(amountToWithdraw, amount.getLow());
		}

		return new TokenHoldingBucket(this.resourceAddr, amount.subtract(withdraw384));
	}


	public void destroy(ReadableAddrs r) throws ProcedureException {
		if (!amount.isZero()) {
			var p = r.loadAddr(null, resourceAddr);
			if (p.isEmpty()) {
				throw new ProcedureException("Token does not exist.");
			}
			var particle = p.get();
			if (!(particle instanceof TokenResource)) {
				throw new ProcedureException("Rri is not a token");
			}
			var tokenDef = (TokenResource) particle;
			if (!tokenDef.isMutable()) {
				throw new ProcedureException("Can only burn mutable tokens.");
			}
		}
	}
}
