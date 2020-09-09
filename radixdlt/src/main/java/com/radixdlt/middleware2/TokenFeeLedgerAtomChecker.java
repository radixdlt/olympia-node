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
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.engine.AtomChecker;
import com.radixdlt.fees.FeeTable;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.utils.UInt256;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Checks that metadata in the ledger atom is well formed and follows what is
 * needed for both consensus and governance.
 */
public class TokenFeeLedgerAtomChecker implements AtomChecker<LedgerAtom> {
	private final FeeTable feeTable;
	private final RRI feeTokenRri;
	private final Serialization serialization;

	@Inject
	public TokenFeeLedgerAtomChecker(
		FeeTable feeTable,
		@Named("feeToken") RRI feeTokenRri,
		Serialization serialization
	) {
		this.feeTable = feeTable;
		this.feeTokenRri = feeTokenRri;
		this.serialization = serialization;
	}

	@Override
	public Result check(LedgerAtom atom, Set<Particle> outputParticles) {
		if (atom.getCMInstruction().getMicroInstructions().isEmpty()) {
			return Result.error("atom has no instructions");
		}

		final boolean magic = Objects.equals(atom.getMetaData().get("magic"), "0xdeadbeef");
		if (!magic) {
			int feeSize = computeFeeSize(atom);
			UInt256 requiredMinimumFee = feeTable.feeFor(atom, outputParticles, feeSize);
			UInt256 feePaid = computeFeePaid(outputParticles);
			if (feePaid.compareTo(requiredMinimumFee) < 0) {
				String message = String.format("atom fee invalid: '%s' is less than required minimum '%s'", feePaid, requiredMinimumFee);
				return Result.error(message);
			}
		}

		return Result.success();
	}

	private int computeFeeSize(LedgerAtom atom) {
		// FIXME: Should remove at least deser here and do somewhere where it can be more efficient
		final Atom completeAtom;
		if (atom instanceof ClientAtom) {
			completeAtom = ClientAtom.convertToApiAtom((ClientAtom) atom);
		} else if (atom instanceof CommittedAtom) {
			completeAtom = ClientAtom.convertToApiAtom(((CommittedAtom) atom).getClientAtom());
		} else {
			throw new IllegalStateException("Unknown LedgerAtom type: " + atom.getClass());
		}
		Atom atomWithoutFeeGroup = completeAtom.copyExcludingGroups(this::isFeeGroup);
		return this.serialization.toDson(atomWithoutFeeGroup, Output.HASH).length;
	}

	private boolean isFeeGroup(ParticleGroup pg) {
		// No free storage in metadata
		if (!pg.getMetaData().isEmpty()) {
			return false;
		}
		Map<Class<? extends Particle>, List<SpunParticle>> grouping = pg.getParticles().stream()
			.collect(Collectors.groupingBy(sp -> sp.getParticle().getClass()));
		List<SpunParticle> spunTransferableTokens = grouping.remove(TransferrableTokensParticle.class);
		List<SpunParticle> spunUnallocatedTokens = grouping.remove(UnallocatedTokensParticle.class);
		// If there is other "stuff" in the group, or no "burn+change", then it's not a fee group
		if (grouping.isEmpty() && spunTransferableTokens != null && spunUnallocatedTokens != null) {
			List<TransferrableTokensParticle> transferableTokens = spunTransferableTokens.stream()
				.map(SpunParticle::getParticle)
				.map(p -> (TransferrableTokensParticle) p)
				.collect(Collectors.toList());
			return allUpForFeeToken(spunUnallocatedTokens) && allSameAddressAndForFee(transferableTokens);
		}
		return false;
	}

	// Check that all transferable particles are in for the same address and for the fee token
	private boolean allSameAddressAndForFee(List<TransferrableTokensParticle> transferableTokens) {
		RadixAddress addr = transferableTokens.get(0).getAddress();
		return transferableTokens.stream()
			.allMatch(ttp -> ttp.getAddress().equals(addr) && this.feeTokenRri.equals(ttp.getTokDefRef()));
	}

	// Check that all unallocated particles are in the up state and for the fee token
	private boolean allUpForFeeToken(List<SpunParticle> spunUnallocatedTokens) {
		return spunUnallocatedTokens.stream()
			.allMatch(this::isUpAndForFee);
	}

	private boolean isUpAndForFee(SpunParticle sp) {
		if (sp.isUp()) {
			UnallocatedTokensParticle utp = (UnallocatedTokensParticle) sp.getParticle();
			return this.feeTokenRri.equals(utp.getTokDefRef());
		}
		return false;
	}

	private UInt256 computeFeePaid(Set<Particle> outputParticles) {
		// We can use UInt256 here, as all fees are paid in a single token
		// type.  As there can be no more than UInt256.MAX_VALUE tokens of
		// a given type, a UInt256 cannot overflow.
		UInt256 feePaid = UInt256.ZERO;
		for (Particle p : outputParticles) {
			if (p instanceof UnallocatedTokensParticle) {
				UnallocatedTokensParticle utp = (UnallocatedTokensParticle) p;
				if (this.feeTokenRri.equals(utp.getTokDefRef())) {
					feePaid = feePaid.add(utp.getAmount());
				}
			}
		}
		return feePaid;
	}
}
