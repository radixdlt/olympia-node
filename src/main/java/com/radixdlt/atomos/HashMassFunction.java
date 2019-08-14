package com.radixdlt.atomos;

import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.UInt384;
import java.util.Objects;

public class HashMassFunction {
	// FIXME: This whole class needs a general review to confirm or otherwise
	// that assumptions made here are still valild.
	// See https://radixdlt.atlassian.net/browse/RLAU-1013
	private static final UInt384 GENESIS_MASS = UInt384.from(8192);
	private static final UInt384 MULTIPLIER = UInt384.from(MutableSupplyTokenDefinitionParticle.SUB_UNITS);

	private final Universe universe;

	public HashMassFunction(Universe universe) {
		this.universe = Objects.requireNonNull(universe);
	}

	public UInt384 getMass(ImmutableAtom atom) {
		UInt384 mass;

		// Special case for Genesis Atom
		// TODO: Remove this special case for genesis
		if (this.universe.getGenesis().contains(atom)) {
			mass = GENESIS_MASS;
		} else {
			// Range for exponent is between -INF..-0.69314
			// Note we take the absolute value after converting to double to avoid
			// the case where Math.abs(Long.MIN_VALUE) < 0
			double exponent = Math.log(Math.abs(atom.getAID().getLow() / (double) Long.MAX_VALUE));
			long lmass = (long) (8192 * Math.pow(10.0, exponent)) + 1;
			mass = UInt384.from(lmass);
		}

		return mass.multiply(MULTIPLIER);
	}
}