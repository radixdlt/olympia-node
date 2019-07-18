package com.radixdlt.atomos;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atoms.Particle;
import com.radixdlt.utils.UInt256;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A fixed composition of fungibles defining an amount of fungible types required for a single unit of the target type
 */
public class FungibleComposition {
	private final ImmutableMap<Class<? extends Particle>, UInt256> requiredAmountsPerUnit;

	private FungibleComposition(ImmutableMap<Class<? extends Particle>, UInt256> requiredAmountsPerUnit) {
		this.requiredAmountsPerUnit = requiredAmountsPerUnit;
	}

	/**
	 * The amounts required by type for a certain amount
	 * @param toAmount The target amount
	 * @return The amounts required
	 */
	public ImmutableMap<Class<? extends Particle>, UInt256> requiredAmounts(UInt256 toAmount) {
		return ImmutableMap.copyOf(this.requiredAmountsPerUnit.entrySet().stream()
			.collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().multiply(toAmount))));
	}

	/**
	 * The amounts required by type for a single unit
	 * @return The amount required
	 */
	public ImmutableMap<Class<? extends Particle>, UInt256> requiredAmountsPerUnit() {
		return this.requiredAmountsPerUnit;
	}

	@Override
	public String toString() {
		return this.requiredAmountsPerUnit.entrySet().stream()
			.map(e -> e.getValue() + " " + e.getKey().getSimpleName())
			.collect(Collectors.joining(" + "));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		FungibleComposition that = (FungibleComposition) o;
		return Objects.equals(requiredAmountsPerUnit, that.requiredAmountsPerUnit);
	}

	@Override
	public int hashCode() {
		return Objects.hash(requiredAmountsPerUnit);
	}

	/**
	 * Create a {@link FungibleComposition} of a given amount of a given Particle class
	 */
	public static FungibleComposition of(Class<? extends Particle> particleClass) {
		return new FungibleComposition(ImmutableMap.of(particleClass, UInt256.ONE));
	}
}
