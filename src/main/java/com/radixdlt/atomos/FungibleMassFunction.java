package com.radixdlt.atomos;

import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Offset;
import com.radixdlt.utils.UInt384;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class FungibleMassFunction {
	private final Universe universe;

	public FungibleMassFunction(Universe universe) {
		this.universe = Objects.requireNonNull(universe);
	}

	public UInt384 getMass(CMAtom atom) {
		Optional<RRI> nativeToken = this.universe.getGenesis().stream()
			.flatMap(a -> a.particles(TransferrableTokensParticle.class, Spin.UP))
			.map(TransferrableTokensParticle::getTokDefRef)
			.filter(p -> p.getName().equals(TokenDefinitionUtils.getNativeTokenShortCode()))
			.findFirst();

		if (!nativeToken.isPresent()) {
			throw new IllegalStateException("No native token found");
		}

		final Function<TransferrableTokensParticle, UInt384> massFunc;
		if (this.universe.getGenesis().contains(atom)) {
			// FIXME: At this point, we don't expect to see any DOWN fungibles in the genesis
			// atoms, but if we do find one, we apply a 1 planck time mass, even though the
			// timestamp and planck for the genesis atom and particle are/should be equivalent.
			// See https://radixdlt.atlassian.net/browse/RLAU-1013
			massFunc = p -> planckMass(1, p);
		} else {
			// Note that the atom planck *should* be after the fungible planck.
			long atomPlanck = this.universe.toPlanck(atom.getTimestamp(), Offset.NONE);
			massFunc = p -> planckMass(atomPlanck - p.getPlanck(), p);
		}

		// TODO: How to support different, non-native Token mass
		return atom.getParticles().stream()
			.filter(p -> p.getParticle() instanceof TransferrableTokensParticle && p.nextSpins().anyMatch(Spin.DOWN::equals))
			.map(p -> (TransferrableTokensParticle) p.getParticle())
			.filter(p -> p.getTokDefRef().equals(nativeToken.get()))
			.map(massFunc)
			.reduce(UInt384.ZERO, UInt384::add);
	}

	private UInt384 planckMass(long planckDiff, TransferrableTokensParticle p) {
		// Avoid unexpected underflow here - when converting to unsigned, this will result in
		// considerably larger mass than expected.
		// In the negative planck/mass case here, we just clamp the value at zero.
		return (planckDiff <= 0) ? UInt384.ZERO : UInt384.from(planckDiff).multiply(p.getAmount());
	}
}
