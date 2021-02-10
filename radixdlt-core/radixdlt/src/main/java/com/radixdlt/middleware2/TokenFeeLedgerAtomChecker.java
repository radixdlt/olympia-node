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

import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.engine.AtomChecker;
import com.radixdlt.fees.FeeTable;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.statecomputer.CommittedAtom;
import com.radixdlt.utils.UInt256;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
	public Result check(LedgerAtom atom) {
		if (atom.getCMInstruction().getMicroInstructions().isEmpty()) {
			return Result.error("atom has no instructions");
		}

		// FIXME: Magic should be removed at some point
		if (isMagic(atom)) {
			return Result.success();
		}

		// no need for fees if a system update
		// TODO: update should also have no message
		if (atom.getCMInstruction().getMicroInstructions().stream()
				.filter(CMMicroInstruction::isCheckSpin)
				.allMatch(i -> i.getParticle() instanceof SystemParticle)
		) {
			return Result.success();
		}

		// FIXME: Should remove at least deser here and do somewhere where it can be more efficient
		final Atom completeAtom;
		if (atom instanceof ClientAtom) {
			completeAtom = ClientAtom.convertToApiAtom((ClientAtom) atom);
		} else if (atom instanceof CommittedAtom) {
			completeAtom = ClientAtom.convertToApiAtom(((CommittedAtom) atom).getClientAtom());
		} else {
			throw new IllegalStateException("Unknown LedgerAtom type: " + atom.getClass());
		}

		final int totalSize = this.serialization.toDson(atom, Output.PERSIST).length;
		if (totalSize > MAX_ATOM_SIZE) {
			return Result.error("atom too big: " + totalSize);
		}

		Atom atomWithoutFeeGroup = completeAtom.copyExcludingGroups(this::isFeeGroup);
		Set<Particle> outputParticles = atomWithoutFeeGroup.particles(Spin.UP).collect(ImmutableSet.toImmutableSet());
		int feeSize = this.serialization.toDson(atomWithoutFeeGroup, Output.HASH).length;

		UInt256 requiredMinimumFee = feeTable.feeFor(atom, outputParticles, feeSize);
		UInt256 feePaid = computeFeePaid(completeAtom.particleGroups().filter(this::isFeeGroup));
		if (feePaid.compareTo(requiredMinimumFee) < 0) {
			String message = String.format("atom fee invalid: '%s' is less than required minimum '%s'", feePaid, requiredMinimumFee);
			return Result.error(message);
		}

		return Result.success();
	}

	private boolean isMagic(LedgerAtom atom) {
		final var message = atom.getMessage();
		return message != null && message.startsWith("magic:0xdeadbeef");
	}

	private boolean isFeeGroup(ParticleGroup pg) {
		Map<Class<? extends Particle>, List<SpunParticle>> grouping = pg.getParticles().stream()
				.collect(Collectors.groupingBy(sp -> sp.getParticle().getClass()));
		List<SpunParticle> spunTransferableTokens = Optional.ofNullable(grouping.remove(TransferrableTokensParticle.class))
				.orElseGet(List::of);
		List<SpunParticle> spunUnallocatedTokens = Optional.ofNullable(grouping.remove(UnallocatedTokensParticle.class))
				.orElseGet(List::of);

		// If there is other "stuff" in the group, or no "burns", then it's not a fee group
		if (!grouping.isEmpty() || spunTransferableTokens.isEmpty() || spunUnallocatedTokens.isEmpty()) {
			return false;
		}

		final Map<Spin, List<TransferrableTokensParticle>> transferableParticlesBySpin =
				spunTransferableTokens.stream().collect(
						Collectors.groupingBy(SpunParticle::getSpin,
								Collectors.mapping(sp -> (TransferrableTokensParticle) sp.getParticle(), Collectors.toList())));

		// Needs to be at least some down transferrable tokens
		final var downTransferrableParticles = transferableParticlesBySpin.get(Spin.DOWN);
		if (downTransferrableParticles == null || downTransferrableParticles.isEmpty()) {
			return false;
		}

		return allUpForFeeToken(spunUnallocatedTokens)
				&& allSameAddressAndForFee(transferableParticlesBySpin)
				&& noSuperfluousParticles(transferableParticlesBySpin);
	}

	// Check that all transferable particles are in for the same address and for the fee token
	private boolean allSameAddressAndForFee(Map<Spin, List<TransferrableTokensParticle>> particlesBySpin) {
		final RadixAddress addr = particlesBySpin.get(Spin.DOWN).get(0).getAddress();
		return particlesBySpin.values().stream()
				.allMatch(transferableTokens -> transferableTokens.stream().allMatch(ttp ->
						ttp.getAddress().equals(addr) && this.feeTokenRri.equals(ttp.getTokDefRef())));
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

	// Check that there is at most one output particle and no input particle is smaller
	private boolean noSuperfluousParticles(Map<Spin, List<TransferrableTokensParticle>> particlesBySpin) {
		final List<TransferrableTokensParticle> inputParticles = particlesBySpin.getOrDefault(Spin.DOWN, List.of());
		final List<TransferrableTokensParticle> outputParticles = particlesBySpin.getOrDefault(Spin.UP, List.of());

		if (outputParticles.size() != 1) {
			return outputParticles.isEmpty();
		}
		final UInt256 outputAmount = outputParticles.get(0).getAmount();

		return inputParticles.stream()
				.allMatch(ttp -> ttp.getAmount().compareTo(outputAmount) > 0);
	}

	private UInt256 computeFeePaid(Stream<ParticleGroup> feeParticleGroups) {
		// We can use UInt256 here, as all fees are paid in a single token
		// type.  As there can be no more than UInt256.MAX_VALUE tokens of
		// a given type, a UInt256 cannot overflow.
		return feeParticleGroups
			.flatMap(pg -> pg.particles(Spin.UP))
			.filter(UnallocatedTokensParticle.class::isInstance)
			.map(UnallocatedTokensParticle.class::cast)
			.map(UnallocatedTokensParticle::getAmount)
			.reduce(UInt256.ZERO, UInt256::add);
	}
}
