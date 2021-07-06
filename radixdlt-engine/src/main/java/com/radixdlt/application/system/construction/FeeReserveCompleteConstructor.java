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

package com.radixdlt.application.system.construction;

import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.FeeReserveComplete;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.utils.UInt256;

import java.util.Optional;

public class FeeReserveCompleteConstructor implements ActionConstructor<FeeReserveComplete> {
	private final FeeTable feeTable;
	public FeeReserveCompleteConstructor(FeeTable feeTable) {
		this.feeTable = feeTable;
	}

	@Override
	public void construct(FeeReserveComplete action, TxBuilder builder) throws TxBuilderException {
		var feeReserve = Optional.ofNullable(builder.getFeeReserve()).orElse(UInt256.ZERO);
		int curSize = builder.toLowLevelBuilder().size() + signatureInstructionSize();
		var perByteFee = feeTable.getPerByteFee().toSubunits();
		var resourceCost = feeTable.getPerResourceFee().toSubunits().multiply(UInt256.from(builder.getNumResourcesCreated()));
		var txnBytesCost = perByteFee.multiply(UInt256.from(curSize));
		var expectedFee1 = txnBytesCost.add(resourceCost);
		if (feeReserve.compareTo(expectedFee1) < 0) {
			throw new FeeReserveCompleteException(feeReserve, expectedFee1);
		}

		if (feeReserve.compareTo(expectedFee1) == 0) {
			// Fees all accounted for, no need to add END
			return;
		}

		// TODO: Figure out cleaner way to do this which isn't so fragile
		var expectedSizeWithNoReturn = curSize + syscallInstructionSize() + endInstructionSize();
		var expectedFee = perByteFee.multiply(UInt256.from(expectedSizeWithNoReturn)).add(resourceCost);
		if (!expectedFee.equals(feeReserve)) {
			var expectedSizeWithReturn = expectedSizeWithNoReturn + returnedSubstateInstructionSize();
			expectedFee = perByteFee.multiply(UInt256.from(expectedSizeWithReturn)).add(resourceCost);
		}
		if (feeReserve.compareTo(expectedFee) < 0) {
			throw new FeeReserveCompleteException(feeReserve, expectedFee);
		}
		var leftover = feeReserve.subtract(expectedFee);
		builder.takeFeeReserve(action.to(), leftover);
		builder.end();
	}

	private static int endInstructionSize() {
		return 1;
	}

	private static int signatureInstructionSize() {
		return 1 + 32 + 32 + 1;
	}

	private static int syscallInstructionSize() {
		var syscallSize = 0;
		syscallSize++; // REInstruction
		syscallSize++; // size
		syscallSize++; // syscall id
		syscallSize += UInt256.BYTES;
		return syscallSize;
	}

	private static int returnedSubstateInstructionSize() {
		var returnSubstateSize = 0;
		returnSubstateSize++; // REInstruction
		returnSubstateSize += 2; // Substate size
		returnSubstateSize++; // Substate typeId
		returnSubstateSize++; // Reserved
		returnSubstateSize += ECPublicKey.COMPRESSED_BYTES + 1; // PubKey addr
		returnSubstateSize++; // Native token addr
		returnSubstateSize += UInt256.BYTES; // amount
		return returnSubstateSize;
	}
}
