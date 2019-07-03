package com.radixdlt.atomos.procedures.fungible;

import com.google.common.collect.ImmutableList;
import com.radixdlt.atoms.Particle;
import com.radixdlt.utils.UInt256;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An immutable set of FungibleOutputs with Output-only operations
 */
// @PackageLocalForTest
final class FungibleOutputs {
	private final ImmutableList<Fungible> fungiblesInOrder;

	private FungibleOutputs(Stream<Fungible> fungibles) {
		this.fungiblesInOrder = fungibles
			.filter(f -> !f.isZero())
			.sorted(Comparator.comparingInt(Fungible::getIndex))
			.collect(ImmutableList.toImmutableList());
	}

	/**
	 * Group these FungibleOutputs using a certain group function
	 *
	 * @param groupFunction The grouping function
	 * @param <K>           The type of key to group by
	 * @return The fungibles in this FungibleOutputs, grouped using the group function
	 */
	<K> Map<K, FungibleOutputs> group(Function<Fungible, K> groupFunction) {
		Map<K, List<Fungible>> groupedFungibles = new HashMap<>();
		for (Fungible fungible : this.fungiblesInOrder) {
			K group = groupFunction.apply(fungible);
			groupedFungibles.computeIfAbsent(group, g -> new ArrayList<>()).add(fungible);
		}
		Map<K, FungibleOutputs> groupedFungibleOutputs = new HashMap<>();
		for (Map.Entry<K, List<Fungible>> fungibles : groupedFungibles.entrySet()) {
			groupedFungibleOutputs.put(fungibles.getKey(), new FungibleOutputs(fungibles.getValue().stream()));
		}
		return groupedFungibleOutputs;
	}

	/**
	 * Get these FungibleInputs in the correct order (by index)
	 */
	Stream<Fungible> fungibles() {
		return this.fungiblesInOrder.stream();
	}

	/**
	 * Whether these FungibleOutputs contain the given {@link Particle}
	 */
	boolean contains(Particle particle) {
		return this.fungibles().anyMatch(f -> f.getParticle().equals(particle));
	}

	/**
	 * The amount of the given particle as contained in this FungibleInputs instance
	 * Note: Returns ZERO if the fungible is not known
	 *
	 * @param particle The particle
	 * @return The consumable amount of the given particle
	 */
	UInt256 amount(Particle particle) {
		return this.fungibles()
			.filter(f -> f.getParticle().equals(particle))
			.map(Fungible::getAmount)
			.reduce(UInt256.ZERO, UInt256::add);
	}

	/**
	 * Whether this contains any non-zero fungibles
	 *
	 * @return Whether this contains any non-zero fungibles
	 */
	boolean isEmpty() {
		return this.fungiblesInOrder.isEmpty();
	}

	/**
	 * Consume the given {@link FungibleInputs} and return the remaining {@link FungibleInputs}
	 * Note that no instances will be modified and only the result will be returned.
	 *
	 * @param fungibleInputs The fungibleInputs to consume
	 * @return The remaining fungibleInputs once consumed by these FungibleOutputs
	 */
	FungibleInputs consume(FungibleInputs fungibleInputs) {
		Objects.requireNonNull(fungibleInputs, "consumed is required");

		final Map<Particle, List<MutableFungible>> remaining = fungibleInputs.fungibles()
			.map(Fungible::asMutable)
			.collect(Collectors.groupingBy(f -> f.getFungible().getParticle()));
		for (Fungible output : this.fungiblesInOrder) {
			List<MutableFungible> remainingInput = remaining.get(output.getParticle());
			if (remainingInput == null) {
				throw new IllegalArgumentException("Unexpected consumer " + output.getParticle());
			}

			UInt256 remainingOutputAmount = output.getAmount();
			for (MutableFungible input : remainingInput) {
				if (!input.getAmount().isZero()) {
					if (remainingOutputAmount.compareTo(input.getAmount()) > 0) {
						remainingOutputAmount = remainingOutputAmount.subtract(input.getAmount());
						input.setAmount(UInt256.ZERO);
					} else {
						input.subtract(remainingOutputAmount);
						remainingOutputAmount = UInt256.ZERO;
					}
				}

				if (remainingOutputAmount.isZero()) {
					break;
				}
			}

			if (!remainingOutputAmount.isZero()) {
				throw new IllegalArgumentException(String.format("Cannot consume %s of %s to %s, not enough available",
					output.getAmount(), remaining.get(output.getParticle()), output));
			}
		}

		return FungibleInputs.of(remaining.values().stream()
			.flatMap(Collection::stream)
			.filter(f -> !f.getAmount().isZero())
			.map(MutableFungible::asFungible));
	}

	/**
	 * Materialize a FungibleOutputs instance by merging a FungibleOutputs instance with additional fungibles
	 */
	static FungibleOutputs of(FungibleOutputs fungibleOutputs1, Fungible... additionalFungibles) {
		Objects.requireNonNull(fungibleOutputs1, "fungibleOutputs1 is required");
		Objects.requireNonNull(additionalFungibles, "additionalFungibles is required");

		return FungibleOutputs.of(Stream.concat(fungibleOutputs1.fungibles(), Arrays.stream(additionalFungibles)));
	}

	/**
	 * Materialize a FungibleOutputs instance by merging two FungibleOutputs instances
	 */
	static FungibleOutputs of(FungibleOutputs fungibleOutputs1, FungibleOutputs fungibleOutputs2) {
		Objects.requireNonNull(fungibleOutputs1, "fungibleOutputs1 is required");
		Objects.requireNonNull(fungibleOutputs2, "fungibleOutputs2 is required");

		return FungibleOutputs.of(Stream.concat(fungibleOutputs1.fungibles(), fungibleOutputs2.fungibles()));
	}

	/**
	 * Materialize FungibleOutputs out of a list of non-zero Fungibles
	 *
	 * @param fungibles The fungibles
	 * @return A FungibleOutputs object containing the given fungibles
	 */
	static FungibleOutputs of(Stream<Fungible> fungibles) {
		return new FungibleOutputs(fungibles);
	}

	/**
	 * Get the absolute difference between two FungibleOutputs
	 */
	static FungibleOutputs diff(FungibleOutputs fungibleOutputs1, FungibleOutputs fungibleOutputs2) {
		return FungibleOutputs.of(Fungible.diff(fungibleOutputs1.fungiblesInOrder, fungibleOutputs2.fungiblesInOrder));
	}

	/**
	 * Materialize an empty FungibleOutputs instance
	 */
	static FungibleOutputs of() {
		return new FungibleOutputs(Stream.empty());
	}
}
