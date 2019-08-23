package com.radixdlt.atomos;

import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.UInt384;

public final class FungibleOrHashMassFunction {
	private final FungibleMassFunction fungibleMassFunction;
	private final HashMassFunction hashMassFunction;

	public FungibleOrHashMassFunction(Universe universe) {
		this.fungibleMassFunction = new FungibleMassFunction(universe);
		this.hashMassFunction = new HashMassFunction(universe);
	}

	public UInt384 getMass(CMInstruction atom) {
		UInt384 mass = fungibleMassFunction.getMass(atom);
		return mass.isZero() ? hashMassFunction.getMass(atom) : mass;
	}
}
