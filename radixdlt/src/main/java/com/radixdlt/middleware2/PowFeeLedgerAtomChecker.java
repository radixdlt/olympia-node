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
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.crypto.Hash;
import com.radixdlt.engine.AtomChecker;
import com.radixdlt.identifiers.AID;
import com.radixdlt.universe.Universe;

import java.util.Objects;
import java.util.Set;

/**
 * Checks that metadata in the ledger atom is well formed and follows what is
 * needed for both consensus and governance.
 */
public class PowFeeLedgerAtomChecker implements AtomChecker<LedgerAtom> {
	private final Set<AID> genesisAtomIDs;
	private final PowFeeComputer powFeeComputer;

	private final Hash target;

	public PowFeeLedgerAtomChecker(
		Universe universe,
		PowFeeComputer powFeeComputer,
		Hash target
	) {
		this.genesisAtomIDs = universe.getGenesis().stream()
			.map(Atom::getAID)
			.collect(ImmutableSet.toImmutableSet());
		this.powFeeComputer = powFeeComputer;
		this.target = target;
	}

	@Override
	public Result check(LedgerAtom atom) {
		if (atom.getCMInstruction().getMicroInstructions().isEmpty()) {
			return Result.error("atom has no instructions");
		}

		final boolean isMagic = Objects.equals(atom.getMetaData().get("magic"), "0xdeadbeef");

		// Atom has fee
		boolean isGenesis = this.genesisAtomIDs.contains(atom.getAID());
		if (!isGenesis && !isMagic) {
			String powNonceString = atom.getMetaData().get(Atom.METADATA_POW_NONCE_KEY);
			if (powNonceString == null) {
				return Result.error("atom fee missing, metadata does not contain '" + Atom.METADATA_POW_NONCE_KEY + "'");
			}

			final long powNonce;
			try {
				powNonce = Long.parseLong(powNonceString);
			} catch (NumberFormatException e) {
				return Result.error("atom fee invalid, metadata contains invalid powNonce: " + powNonceString);
			}

			Hash powSpent = powFeeComputer.computePowSpent(atom, powNonce);
			if (powSpent.compareTo(target) >= 0) {
				return Result.error("atom fee invalid: '" + powSpent + "' does not meet target '" + target + "'");
			}
		}

		return Result.success();
	}
}
