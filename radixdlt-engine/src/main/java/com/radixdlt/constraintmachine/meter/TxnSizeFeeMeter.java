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

package com.radixdlt.constraintmachine.meter;

import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.constraintmachine.ExecutionContext;
import com.radixdlt.constraintmachine.ProcedureKey;
import com.radixdlt.constraintmachine.REOp;
import com.radixdlt.utils.UInt256;

public class TxnSizeFeeMeter implements Meter {
	private final UInt256 feePerByte;
	private final UInt256 systemLoan;

	private TxnSizeFeeMeter(UInt256 feePerByte, UInt256 systemLoan) {
		this.feePerByte = feePerByte;
		this.systemLoan = systemLoan;
	}

	public static TxnSizeFeeMeter create(UInt256 feePerByte, long maxSize) {
		var systemLoan = feePerByte.multiply(UInt256.from(maxSize));
		return new TxnSizeFeeMeter(feePerByte, systemLoan);
	}

	@Override
	public void onStart(ExecutionContext context) {
		context.addSystemLoan(systemLoan);
	}

	@Override
	public void onUserProcedure(ProcedureKey procedureKey, Object param, ExecutionContext context) throws Exception {
		context.chargeOneTimeTransactionFee(txn -> UInt256.from(txn.getPayload().length).multiply(feePerByte));

		if (procedureKey.opSignature().op() == REOp.SYSCALL) {
			return;
		}

		// TODO: Clean this up
		if (procedureKey.opSignature().op() == REOp.DOWN) {
			if (param instanceof TokensInAccount) {
				var tokensInAccount = (TokensInAccount) param;
				if (tokensInAccount.getResourceAddr().isNativeToken()) {
					return;
				}
			}
		}

		context.payOffLoan();
	}

	@Override
	public void onSuperUserProcedure(ProcedureKey procedureKey, Object param, ExecutionContext context) throws Exception {
		context.payOffLoan();
	}

	@Override
	public void onSigInstruction(ExecutionContext context) {
		// No-op
	}
}
