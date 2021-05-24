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

package com.radixdlt.statecomputer.transaction;

import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.ConstraintMachineException;
import com.radixdlt.constraintmachine.REParsedAction;
import com.radixdlt.constraintmachine.REParsedTxn;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.engine.PostParsedChecker;
import com.radixdlt.utils.UInt256;

/**
 * Checks that metadata in the ledger atom is well formed and follows what is
 * needed for both consensus and governance.
 */
public class TokenFeeChecker implements PostParsedChecker {
	public static final UInt256 FIXED_FEE = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 - 3)
		.multiply(UInt256.from(100));

	@Override
	public void check(PermissionLevel permissionLevel, REParsedTxn radixEngineTxn) throws ConstraintMachineException {
		if (permissionLevel.equals(PermissionLevel.SYSTEM)) {
			return;
		}

		// no fees for system updates
		if (radixEngineTxn.isSystemOnly()) {
			return;
		}

		// FIXME: This logic needs to move into the constraint machine and must be first
		// FIXME: action checked
		var feePaid = computeFeePaid(radixEngineTxn);
		if (feePaid.compareTo(FIXED_FEE) < 0) {
			throw new ConstraintMachineException(
				CMErrorCode.FEE_NOT_FOUND,
				null,
				String.format("atom fee invalid: '%s' is less than fixed fee of '%s'",
					TokenUnitConversions.subunitsToUnits(feePaid),
					TokenUnitConversions.subunitsToUnits(FIXED_FEE)
				)
			);
		}
	}

	private UInt256 computeFeePaid(REParsedTxn radixEngineTxn) {
		return radixEngineTxn.getActions()
			.stream()
			.map(REParsedAction::getTxAction)
			.filter(BurnToken.class::isInstance)
			.map(BurnToken.class::cast)
			.filter(t -> t.resourceAddr().isNativeToken())
			.map(BurnToken::amount)
			.reduce(UInt256::add)
			.orElse(UInt256.ZERO);
	}
}
