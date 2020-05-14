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
import com.radixdlt.atommodel.Atom;
import com.radixdlt.crypto.Hash;
import com.radixdlt.engine.CMSuccessHook;
import com.radixdlt.universe.Universe;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Checks that metadata in the ledger atom is well formed and follows what is
 * needed for both consensus and governance.
 */
public class LedgerAtomChecker implements CMSuccessHook<LedgerAtom> {
	private final boolean skipAtomFeeCheck;
	private final Supplier<Universe> universeSupplier;
	private final LongSupplier timestampSupplier;
	private final PowFeeComputer powFeeComputer;

	private final int maximumDrift;
	private final Hash target;

	public LedgerAtomChecker(
		Supplier<Universe> universeSupplier,
		LongSupplier timestampSupplier,
		PowFeeComputer powFeeComputer,
		Hash target,
		boolean skipAtomFeeCheck,
		int maximumDrift
	) {
		this.universeSupplier = universeSupplier;
		this.timestampSupplier = timestampSupplier;
		this.powFeeComputer = powFeeComputer;
		this.target = target;
		this.skipAtomFeeCheck = skipAtomFeeCheck;
		this.maximumDrift = maximumDrift;
	}

	@Override
	public Result hook(LedgerAtom atom) {
		if (atom.getCMInstruction().getMicroInstructions().isEmpty()) {
			return Result.error("atom has no instructions");
		}

		final boolean isMagic = Objects.equals(atom.getMetaData().get("magic"), "0xdeadbeef");

		// Atom has fee
		if (!skipAtomFeeCheck) {
			boolean isGenesis = universeSupplier.get().getGenesis().stream().map(Atom::getAID).anyMatch(atom.getAID()::equals);
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
		}

		String timestampString = atom.getMetaData().get(Atom.METADATA_TIMESTAMP_KEY);
		if (timestampString == null) {
			return Result.error("atom metadata does not contain '" + Atom.METADATA_TIMESTAMP_KEY + "'");
		}
		try {
			long timestamp = Long.parseLong(timestampString);

			if (timestamp > timestampSupplier.getAsLong()
				+ TimeUnit.MILLISECONDS.convert(maximumDrift, TimeUnit.SECONDS)) {
				return Result.error("atom metadata timestamp is after allowed drift time");
			}
			if (timestamp < universeSupplier.get().getTimestamp()) {
				return Result.error("atom metadata timestamp is before universe creation");
			}
		} catch (NumberFormatException e) {
			return Result.error("atom metadata contains invalid timestamp: " + timestampString);
		}

		return Result.success();
	}
}
