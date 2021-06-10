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

package com.radixdlt.atommodel.system.scrypt;

import com.radixdlt.atommodel.tokens.scrypt.TokenHoldingBucket;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.atomos.Loader;
import com.radixdlt.constraintmachine.Authorization;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ReducerResult;
import com.radixdlt.constraintmachine.SystemCallProcedure;
import com.radixdlt.identifiers.REAddr;

public final class FeeConstraintScrypt implements ConstraintScrypt {
	@Override
	public void main(Loader os) {
		os.procedure(
			new SystemCallProcedure<>(
				TokenHoldingBucket.class, REAddr.ofSystem(),
				() -> new Authorization(PermissionLevel.USER, (r, c) -> { }),
				(s, d, c) -> {
					var type = d.get(0);
					if (type != 0) {
						throw new ProcedureException("Invalid call type: " + type);
					}

					var amt = d.getUInt256(1);
					var nextState = s.withdraw(REAddr.ofNativeToken(), amt);
					c.depositFeeReserve(amt);
					return ReducerResult.incomplete(nextState);
				}
			)
		);
	}
}
