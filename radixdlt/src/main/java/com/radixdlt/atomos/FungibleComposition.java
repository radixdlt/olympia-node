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
	public static FungibleComposition of(long amount, Class<? extends Particle> particleClass) {
		return new FungibleComposition(ImmutableMap.of(particleClass, UInt256.from(amount)));
	}

	/**
	 * Create a {@link FungibleComposition} of a given amount of a given Particle class
	 */
	public static FungibleComposition of(UInt256 amount, Class<? extends Particle> particleClass) {
		return new FungibleComposition(ImmutableMap.of(particleClass, amount));
	}

	/**
	 * Create a {@link FungibleComposition} of a two given amounts of two given Particle class
	 */
	public static FungibleComposition of(long amount1, Class<? extends Particle> particleClass1,
	                                     long amount2, Class<? extends Particle> particleClass2) {
		return new FungibleComposition(ImmutableMap.of(particleClass1, UInt256.from(amount1), particleClass2, UInt256.from(amount2)));
	}

	/**
	 * Create a {@link FungibleComposition} of a two given amounts of two given Particle class
	 */
	public static FungibleComposition of(UInt256 amount1, Class<? extends Particle> particleClass1,
	                                     UInt256 amount2, Class<? extends Particle> particleClass2) {
		return new FungibleComposition(ImmutableMap.of(particleClass1, amount1, particleClass2, amount2));
	}

	/**
	 * Create a {@link FungibleComposition} of a set of amounts by their Particle classes
	 */
	public static FungibleComposition ofLong(Map<Class<? extends Particle>, Long> requiredAmounts) {
		Map<Class<? extends Particle>, UInt256> requiredAmountsConverted = new HashMap<>();
		requiredAmounts.forEach((cls, amount) -> requiredAmountsConverted.put(cls, UInt256.from(amount)));

		return new FungibleComposition(ImmutableMap.copyOf(requiredAmountsConverted));
	}

	/**
	 * Create a {@link FungibleComposition} of a set of amounts by their Particle classes
	 */
	public static FungibleComposition of(Map<Class<? extends Particle>, UInt256> requiredAmounts) {
		return new FungibleComposition(ImmutableMap.copyOf(requiredAmounts));
	}
}
