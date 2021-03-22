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

import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.LedgerAtom;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.engine.AtomChecker;
import com.radixdlt.fees.FeeTable;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.utils.UInt256;

import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

/**
 * Checks that metadata in the ledger atom is well formed and follows what is
 * needed for both consensus and governance.
 */
public class TokenFeeLedgerAtomChecker implements AtomChecker<LedgerAtom> {
	private static final int MAX_ATOM_SIZE = 1024 * 1024;

	private final FeeTable feeTable;
	private final RRI feeTokenRri;
	private final Serialization serialization;

	@Inject
	public TokenFeeLedgerAtomChecker(
		FeeTable feeTable,
		@NativeToken RRI feeTokenRri,
		Serialization serialization
	) {
		this.feeTable = feeTable;
		this.feeTokenRri = feeTokenRri;
		this.serialization = serialization;
	}

	@Override
	public Result check(LedgerAtom atom, PermissionLevel permissionLevel) {
		if (atom.getCMInstruction().getMicroInstructions().isEmpty()) {
			return Result.error("atom has no instructions");
		}

		// FIXME: Magic should be removed at some point
		if (isMagic(atom)) {
			return Result.success();
		}

		if (permissionLevel.equals(PermissionLevel.SYSTEM)) {
			return Result.success();
		}

		// FIXME: Should remove at least deser here and do somewhere where it can be more efficient
		final Atom clientAtom;
		if (atom instanceof Atom) {
			clientAtom = (Atom) atom;
		} else if (atom instanceof CommittedAtom) {
			clientAtom = ((CommittedAtom) atom).getAtom();
		} else {
			throw new IllegalStateException("Unknown LedgerAtom type: " + atom.getClass());
		}

		// no need for fees if a system update
		// TODO: update should also have no message
		if (clientAtom.upParticles().allMatch(p -> p instanceof SystemParticle)) {
			return Result.success();
		}

		final int totalSize = this.serialization.toDson(clientAtom, Output.PERSIST).length;
		if (totalSize > MAX_ATOM_SIZE) {
			return Result.error("atom too big: " + totalSize);
		}

		// FIXME: This logic needs to move into the constraint machine
		Set<Particle> outputParticles = clientAtom.upParticles().collect(Collectors.toSet());
		UInt256 requiredMinimumFee = feeTable.feeFor(atom, outputParticles, totalSize);
		UInt256 feePaid = computeFeePaid(clientAtom);
		if (feePaid.compareTo(requiredMinimumFee) < 0) {
			String message = String.format(
				"atom fee invalid: '%s' is less than required minimum '%s' atom_size: %s",
				TokenUnitConversions.subunitsToUnits(feePaid),
				TokenUnitConversions.subunitsToUnits(requiredMinimumFee),
				totalSize
			);
			return Result.error(message);
		}

		return Result.success();
	}

	private boolean isMagic(LedgerAtom atom) {
		final var message = atom.getMessage();
		return message != null && message.startsWith("magic:0xdeadbeef");
	}

	// TODO: Need to make sure that these unallocated particles are never DOWNED.
	private UInt256 computeFeePaid(Atom atom) {
		return atom.upParticles()
			.filter(UnallocatedTokensParticle.class::isInstance)
			.map(UnallocatedTokensParticle.class::cast)
			.filter(u -> u.getTokDefRef().equals(this.feeTokenRri))
			.map(UnallocatedTokensParticle::getAmount)
			.reduce(UInt256.ZERO, UInt256::add);
	}
}
