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

package com.radixdlt.application.tokens.scrypt;

import com.radixdlt.constraintmachine.ExecutionContext;
import com.radixdlt.constraintmachine.exceptions.InvalidResourceException;
import com.radixdlt.constraintmachine.exceptions.NotAResourceException;
import com.radixdlt.constraintmachine.exceptions.NotEnoughResourcesException;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.constraintmachine.Resources;
import com.radixdlt.constraintmachine.ReducerState;
import com.radixdlt.constraintmachine.exceptions.ResourceAllocationAndDestructionException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

public final class TokenHoldingBucket implements ReducerState {
	private Tokens tokens;

	public TokenHoldingBucket(Tokens tokens) {
		this.tokens = tokens;
	}

	public boolean isEmpty() {
		return tokens.isZero();
	}

	public REAddr getResourceAddr() {
		return tokens.getResourceAddr();
	}

	public void deposit(Tokens tokens) throws InvalidResourceException {
		this.tokens = this.tokens.merge(tokens);
	}

	public Tokens withdraw(REAddr resourceAddr, UInt256 amountToWithdraw) throws InvalidResourceException, NotEnoughResourcesException {
		if (!this.tokens.getResourceAddr().equals(resourceAddr)) {
			throw new InvalidResourceException(resourceAddr, this.tokens.getResourceAddr());
		}

		if (amountToWithdraw.isZero()) {
			return Tokens.zero(resourceAddr);
		}

		var p = this.tokens.split(amountToWithdraw);
		this.tokens = p.getSecond();
		return p.getFirst();
	}

	public void destroy(ExecutionContext c, Resources r) throws ResourceAllocationAndDestructionException, NotAResourceException, ProcedureException {
		if (!tokens.isZero()) {
			c.verifyCanAllocAndDestroyResources();

			var tokenResource = r.loadResource(tokens.getResourceAddr());
			if (!tokenResource.isMutable()) {
				throw new ProcedureException("Can only burn mutable tokens.");
			}
		}
	}

	@Override
	public String toString() {
		return String.format("%s{tokens=%s}", this.getClass().getSimpleName(), tokens);
	}
}
