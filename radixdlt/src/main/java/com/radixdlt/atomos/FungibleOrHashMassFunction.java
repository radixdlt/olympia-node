package com.radixdlt.atomos;

import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.utils.UInt384;
import com.radixdlt.universe.Universe;

public final class FungibleOrHashMassFunction {
	private final FungibleMassFunction fungibleMassFunction;
	private final HashMassFunction hashMassFunction;

	public FungibleOrHashMassFunction(Universe universe) {
		this.fungibleMassFunction = new FungibleMassFunction(universe);
		this.hashMassFunction = new HashMassFunction(universe);
	}

	public UInt384 getMass(ImmutableAtom atom) {
		UInt384 mass = fungibleMassFunction.getMass(atom);
		return mass.isZero() ? hashMassFunction.getMass(atom) : mass;
	}
}
