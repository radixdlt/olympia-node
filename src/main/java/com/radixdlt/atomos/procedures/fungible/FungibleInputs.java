package com.radixdlt.atomos.procedures.fungible;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.radixdlt.atomos.FungibleComposition;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt256s;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An immutable set of FungibleInputs with Input-only operations
 */
// @PackageLocalForTest
final class FungibleInputs {
	private final ImmutableList<Fungible> fungiblesInOrder;

	private FungibleInputs(Stream<Fungible> fungibles) {
		this.fungiblesInOrder = fungibles
			.filter(f -> !f.isZero())
			.sorted(Comparator.comparingInt(Fungible::getIndex))
			.collect(ImmutableList.toImmutableList());
	}

	/**
	 * Group these FungibleInputs using a certain group function
	 *
	 * @param groupFunction The grouping function
	 * @param <K>           The type of key to group by
	 * @return The fungibles in this FungibleInputs, grouped using the group function
	 */
	<K> Map<K, FungibleInputs> group(Function<Fungible, K> groupFunction) {
		return this.fungibles()
			.collect(Collectors.groupingBy(groupFunction)).entrySet().stream()
			.collect(Collectors.toMap(Map.Entry::getKey, e -> FungibleInputs.of(e.getValue().stream())));
	}

	private Pair<Stream<Fungible>, Map<Class<? extends Particle>, UInt256>> drain(
		ImmutableMap<Class<? extends Particle>, UInt256> requiredAmounts, Map<Fungible, List<Class<? extends Particle>>> approvedClasses) {
		List<Fungible> drainedInputs = new ArrayList<>();
		Map<Class<? extends Particle>, UInt256> remainingAmounts = Maps.newHashMap(requiredAmounts);
		Map<Class<? extends Particle>, UInt256> satisfiedAmounts = Maps.newHashMap();

		for (Fungible input : this.fungiblesInOrder) {
			if (remainingAmounts.values().stream().allMatch(UInt256::isZero)) {
				break;
			}

			for (Map.Entry<Class<? extends Particle>, UInt256> remaining : remainingAmounts.entrySet()) {
				if (!remaining.getValue().isZero()
					 && approvedClasses.get(input).contains(remaining.getKey())) {
					UInt256 claimed = UInt256s.min(input.getAmount(), remaining.getValue());

					drainedInputs.add(input.withAmount(claimed));
					remaining.setValue(remaining.getValue().subtract(claimed));
					satisfiedAmounts.merge(remaining.getKey(), claimed, UInt256::add);
				}
			}
		}

		return Pair.of(
			drainedInputs.stream().sorted(Comparator.comparingInt(Fungible::getIndex)),
			satisfiedAmounts
		);
	}

	/**
	 * Match these FungibleInputs against a consumer amount of a certain composition
	 *
	 * @param consumerAmount  The consumer amount
	 * @param composition     The composition
	 * @return A {@link CompositionMatch} with the amount that could be satisfied and the required consumables
	 */
	CompositionMatch match(UInt256 consumerAmount, FungibleComposition composition) {
		Map<Fungible, List<Class<? extends Particle>>> approvedClasses = new HashMap<>();
		for (Fungible fungible : fungiblesInOrder) {
			approvedClasses.put(fungible, Collections.singletonList(fungible.getParticleClass()));
		}
		return match(consumerAmount, composition, approvedClasses);
	}

	/**
	 * Greedily match these FungibleInputs against a consumer amount of a certain composition
	 *
	 * @param outputAmount  The consumer amount
	 * @param composition     The composition
	 * @param approvedClasses The approved classes for every fungible
	 * @return A {@link CompositionMatch} with the amount that could be satisfied and the required consumables
	 */
	CompositionMatch match(UInt256 outputAmount, FungibleComposition composition, Map<Fungible, List<Class<? extends Particle>>> approvedClasses) {
		Objects.requireNonNull(outputAmount, "outputAmount is required");
		Objects.requireNonNull(composition, "composition is required");
		Objects.requireNonNull(approvedClasses, "composition is required");

		if (outputAmount.isZero()) {
			return CompositionMatch.EMPTY;
		}

		for (Fungible fungible : this.fungiblesInOrder) {
			if (!approvedClasses.containsKey(fungible)) {
				throw new IllegalArgumentException("Approved classes not assigned to " + fungible);
			}
		}

		ImmutableMap<Class<? extends Particle>, UInt256> wantedAmounts = composition.requiredAmounts(outputAmount);
		ImmutableMap<Class<? extends Particle>, UInt256> requiredAmountsPerUnit = composition.requiredAmountsPerUnit();
		Optional<UInt256> availableOutputAmount = getAvailableOutputAmount(approvedClasses, wantedAmounts, requiredAmountsPerUnit);

		return availableOutputAmount
			.filter(satisfiedAmount -> !satisfiedAmount.isZero())
			.map(satisfiedAmount -> UInt256s.min(satisfiedAmount, outputAmount))
			.map(satisfiedAmount -> new CompositionMatch(FungibleInputs.of(
				this.drain(composition.requiredAmounts(satisfiedAmount), approvedClasses).getFirst()),
				satisfiedAmount))
			.orElse(CompositionMatch.EMPTY);
	}

	private Optional<UInt256> getAvailableOutputAmount(Map<Fungible, List<Class<? extends Particle>>> approvedClasses,
	                                                   ImmutableMap<Class<? extends Particle>, UInt256> requiredAmounts,
	                                                   ImmutableMap<Class<? extends Particle>, UInt256> requiredAmountsPerUnit) {
		return drain(requiredAmounts, approvedClasses).getSecond().entrySet().stream()
			.map(satisfied -> satisfied.getValue().divide(requiredAmountsPerUnit.get(satisfied.getKey())))
			.min(UInt256::compareTo);
	}

	/**
	 * A match of FungibleInputs against a composition of particles
	 */
	// @PackageLocalForTest
	static final class CompositionMatch {
		static final CompositionMatch EMPTY = new CompositionMatch(FungibleInputs.of(), UInt256.ZERO);

		private final UInt256 satisfiedAmount;
		private final FungibleInputs matchedInputs;

		CompositionMatch(FungibleInputs matchedInputs, UInt256 satisfiedOutput) {
			this.satisfiedAmount = Objects.requireNonNull(satisfiedOutput, "satisfiedAmount is required");
			this.matchedInputs = Objects.requireNonNull(matchedInputs, "matchedInputs is required");
		}

		UInt256 getSatisfiedAmount() {
			return this.satisfiedAmount;
		}

		FungibleInputs consume(FungibleInputs fungibleInputs) {
			return FungibleOutputs.of(matchedInputs.fungibles()).consume(fungibleInputs);
		}

		public FungibleInputs getMatchedInputs() {
			return matchedInputs;
		}

		InputsOutputsMatch withConsumer(Fungible consumer) {
			if (satisfiedAmount.isZero()) {
				return InputsOutputsMatch.EMPTY;
			}

			return new InputsOutputsMatch(consumer.withAmount(satisfiedAmount), matchedInputs);
		}
	}

	/**
	 * Get these FungibleInputs in the correct order (by index)
	 *
	 * @return
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
	 * Get the absolute difference between two FungibleInputs
	 */
	static FungibleInputs diff(FungibleInputs fungibleInputs1, FungibleInputs fungibleInputs2) {
		return FungibleInputs.of(Fungible.diff(fungibleInputs1.fungiblesInOrder, fungibleInputs2.fungiblesInOrder));
	}

	/**
	 * Materialize FungibleInputs out of a list of non-zero Fungibles
	 *
	 * @param fungibles The fungibles
	 * @return A FungibleInputs object containing the given fungibles
	 */
	static FungibleInputs of(Stream<Fungible> fungibles) {
		return new FungibleInputs(fungibles);
	}

	/**
	 * Materialize an empty FungibleOutputs instance
	 */
	static FungibleInputs of() {
		return new FungibleInputs(Stream.empty());
	}
}
