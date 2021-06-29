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

package com.radixdlt.constraintmachine.metering;

import com.radixdlt.atommodel.tokens.state.TokensInAccount;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.ExecutionContext;
import com.radixdlt.constraintmachine.Metering;
import com.radixdlt.constraintmachine.ProcedureKey;
import com.radixdlt.constraintmachine.REOp;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.utils.UInt256;

public class FixedFeeMetering implements Metering {
	private final UInt256 fixedFee;

	public FixedFeeMetering(UInt256 fixedFee) {
		this.fixedFee = fixedFee;
	}

	@Override
	public void onUserInstruction(ProcedureKey procedureKey, Object param, ExecutionContext context) throws AuthorizationException {
		if (procedureKey.opSignature().op() == REOp.SYSCALL) {
			return;
		}

		// TODO: Clean this up
		if (procedureKey.opSignature().op() == REOp.DOWN
			&& param instanceof SubstateWithArg) {
			var substateWithArg = (SubstateWithArg) param;
			if (substateWithArg.getSubstate() instanceof TokensInAccount) {
				var tokensInAccount = (TokensInAccount) substateWithArg.getSubstate();
				if (tokensInAccount.getResourceAddr().isNativeToken()) {
					return;
				}
			}
		}

		context.verifyHasReserve(fixedFee);
	}
}
