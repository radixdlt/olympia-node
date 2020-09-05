/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.middleware2;

import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.engine.AtomChecker;
import com.radixdlt.fees.FeeTable;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.utils.UInt256;

import java.util.Set;

/**
 * Checks that metadata in the ledger atom is well formed and follows what is
 * needed for both consensus and governance.
 */
public class TokenFeeLedgerAtomChecker implements AtomChecker<LedgerAtom> {
	private final FeeTable feeTable;
	private final RRI feeTokenRri;
	private final boolean skipAtomFeeCheck;

	public TokenFeeLedgerAtomChecker(
		FeeTable feeTable,
		RRI feeTokenRri,
		boolean skipAtomFeeCheck
	) {
		this.feeTable = feeTable;
		this.feeTokenRri = feeTokenRri;
		this.skipAtomFeeCheck = skipAtomFeeCheck;
	}

	@Override
	public Result check(LedgerAtom atom, Set<Particle> outputParticles) {
		if (atom.getCMInstruction().getMicroInstructions().isEmpty()) {
			return Result.error("atom has no instructions");
		}

		if (!this.skipAtomFeeCheck) {
			UInt256 requiredMinimumFee = feeTable.feeFor(atom, outputParticles);
			UInt256 feePaid = computeFeePaid(outputParticles);
			if (feePaid.compareTo(requiredMinimumFee) < 0) {
				String message = String.format("atom fee invalid: '%s' is less than required minimum '%s'", feePaid, requiredMinimumFee);
				return Result.error(message);
			}
		}

		return Result.success();
	}

	private UInt256 computeFeePaid(Set<Particle> outputParticles) {
		// We can use UInt256 here, as all fees are paid in a single token
		// type.  As there can be no more than UInt256.MAX_VALUE tokens of
		// a given type, a UInt256 cannot overflow.
		UInt256 feePaid = UInt256.ZERO;
		for (Particle p : outputParticles) {
			if (p instanceof UnallocatedTokensParticle) {
				UnallocatedTokensParticle utp = (UnallocatedTokensParticle) p;
				if (utp.getTokDefRef().equals(this.feeTokenRri)) {
					feePaid = feePaid.add(utp.getAmount());
				}
			}
		}
		return feePaid;
	}
}
