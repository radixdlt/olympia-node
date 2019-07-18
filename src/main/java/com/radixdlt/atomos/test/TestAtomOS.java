package com.radixdlt.atomos.test;

import com.radixdlt.atomos.AtomOS;
import com.radixdlt.atomos.FungibleComposition;
import com.radixdlt.atomos.FungibleFormula;
import com.radixdlt.atomos.FungibleTransition;
import com.radixdlt.atomos.FungibleTransitionMember;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.mapper.ParticleToAmountMapper;
import com.radixdlt.atomos.mapper.ParticleToRRIMapper;
import com.radixdlt.atomos.mapper.ParticleToShardableMapper;
import com.radixdlt.atomos.mapper.ParticleToShardablesMapper;
import com.radixdlt.atomos.procedures.fungible.FungibleTransitionConstraintCheck;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A stubbed AtomOS used for testing Atom Model Application Layer Code.
 * Note that this class is not thread-safe.
 */
public class TestAtomOS implements AtomOS {
	private final List<Pair<Class<? extends Particle>, IndexedConstraintCheck<Particle>>> indexedInitialConstraints = new ArrayList<>();
	private final List<Pair<Class<? extends Particle>, BiFunction<Particle, AtomMetadata, Result>>> particleClassConstraints = new ArrayList<>();
	private final List<Pair<Pair<Class<? extends Particle>, Class<? extends Particle>>,
		ParticleClassWithDependenceConstraintCheck<? extends Particle, ?>>> particleClassWithDependencyConstraints = new ArrayList<>();
	private final List<FungibleTransition<? extends Particle>> fungibleTransitions = new ArrayList<>();
	private final List<Pair<Pair<Class<? extends Particle>, Class<? extends Particle>>,
		ParticleClassWithSideEffectConstraintCheck<? extends Particle, ?>>> particleClassWithSideEffectConstraints = new ArrayList<>();
	private FungibleTransition.Builder<? extends Particle> pendingFungibleTransition = null;

	@Override
	public <T extends Particle> void registerParticle(Class<T> particleClass, String name, ParticleToShardablesMapper<T> mapper) {
		// Not implemented for the test AtomOS for the time being as it is not used to test any functionality.
	}

	@Override
	public <T extends Particle> void registerParticle(Class<T> particleClass, String name, ParticleToShardableMapper<T> mapper) {
		// Not implemented for the test AtomOS for the time being as it is not used to test any functionality.
	}

	@Override
	public <T extends Particle> IndexedConstraint<T> onIndexed(Class<T> particleClass, ParticleToRRIMapper<T> indexer) {
		return constraint -> {
			indexedInitialConstraints.add(new Pair<>(particleClass, (p, m) -> constraint.apply((T) p, m)));
			return new InitializedIndexedConstraint<T>() {
				@Override
				public <U extends Particle> void requireInitialWith(
					Class<U> sideEffectClass,
					ParticleClassWithSideEffectConstraintCheck<T, U> constraint
				) {

				}
			};
		};
	}

	@Override
	public <T extends Particle> PayloadParticleClassConstraint<T> onPayload(Class<T> particleClass) {
		return constraint -> particleClassConstraints.add(new Pair<>(particleClass, (p, m) -> constraint.apply((T) p, m)));
	}


	@Override
	public <T extends Particle> ParticleClassConstraint<T> on(Class<T> particleClass) {
		return constraint -> particleClassConstraints.add(new Pair<>(particleClass, (p, m) -> constraint.apply((T) p)));
	}

	@Override
	public <T extends Particle> FungibleTransitionConstraintStub<T> onFungible(
		Class<T> particleClass,
		ParticleToAmountMapper<T> particleToAmountMapper,
		BiFunction<T, T, Boolean> fungibleEquals
	) {
		if (pendingFungibleTransition != null) {
			fungibleTransitions.add(pendingFungibleTransition.build());
		}

		FungibleTransition.Builder<T> transitionBuilder = FungibleTransition.<T>build()
			.to(particleClass, particleToAmountMapper, fungibleEquals);
		pendingFungibleTransition = transitionBuilder;

		return new FungibleTransitionConstraintStub<T>() {
			@Override
			public <U extends Particle> FungibleTransitionConstraint<T> requireInitialWith(
				Class<U> sideEffectClass,
				ParticleClassWithSideEffectConstraintCheck<T, U> constraint
			) {
				transitionBuilder.initialWith(sideEffectClass, constraint);
				return this::requireFrom;
			}

			@Override
			public <U extends Particle> FungibleTransitionConstraint<T> requireFrom(
				Class<U> cls1,
				FungibleTransitionInputConstraint<U, T> check
			) {
				if (pendingFungibleTransition == null) {
					throw new IllegalStateException("Attempt to add formula to finished fungible transition to " + particleClass);
				} FungibleFormula formula = FungibleFormula.from(new FungibleTransitionMember<>(cls1, check));
				transitionBuilder.addFormula(formula);
				return this::requireFrom;
			}
		};
	}

	/**
	 * Mimics a constraint check call to particle class checkers
	 *
	 * @param <T> class of particle
	 * @param t particle to be sent to the check call
	 * @param metadata
	 * @return list of results from each checker
	 */
	public <T extends Particle> TestResult testInitialParticle(T t, AtomMetadata metadata) {
		Stream<Result> indexedCheckResults = indexedInitialConstraints.stream()
			.filter(p -> p.getFirst().isAssignableFrom(t.getClass()))
			.map(Pair::getSecond)
			.map(constraint -> constraint.apply(t, metadata));

		Stream<Result> classConstraintResults = particleClassConstraints.stream()
			.filter(p -> p.getFirst().isAssignableFrom(t.getClass()))
			.map(Pair::getSecond)
			.map(constraint -> constraint.apply(t, metadata));

		final List<Result> results = Stream.concat(indexedCheckResults, classConstraintResults).collect(Collectors.toList());

		return new TestResult(results);
	}

	/**
	 * Mimics a constraint check call to a particle class with dependency constraint
	 *
	 * @param dependent The dependent
	 * @param dependency The dependency
	 * @param metadata The metadata
	 * @param <T> The dependent class
	 * @param <U> The dependency class
	 * @return list of results from each checker
	 */
	public <T extends Particle, U extends Particle> TestResult testParticleClassWithDependency(T dependent, U dependency, AtomMetadata metadata) {
		return testParticleClassWithDependency(Arrays.asList(dependent), dependency, metadata);
	}

	/**
	 * Mimics a constraint check call to a particle class with dependency constraint
	 *
	 * @param dependents The dependents
	 * @param dependency The dependency
	 * @param metadata The metadata
	 * @param <T> The dependent class
	 * @param <U> The dependency class
	 * @return list of results from each checker
	 */
	public <T extends Particle, U extends Particle> TestResult testParticleClassWithDependency(
		List<T> dependents,
		U dependency,
		AtomMetadata metadata
	) {
		if (dependents.isEmpty()) {
			throw new IllegalStateException("No dependents");
		}

		final List<Result> results = particleClassWithDependencyConstraints.stream()
			.filter(p -> p.getFirst().getFirst().isAssignableFrom(dependents.get(0).getClass()))
			.filter(p -> p.getFirst().getSecond().isAssignableFrom(dependency.getClass()))
			.map(Pair::getSecond)
			.map(constraint -> ((ParticleClassWithDependenceConstraintCheck<T, U>) constraint).check(dependents, dependency, metadata))
			.collect(Collectors.toList());

		return new TestResult(results);
	}

	/**
	 * Mimics a constraint check call to a particle class with side effect
	 *
	 * @param particle The particle
	 * @param sideEffect The side effect
	 * @param metadata The metadata
	 * @param <T> The particle type
	 * @param <U> The side effect type
	 * @return list of results from each checkers
	 */
	public <T extends Particle, U extends Particle> TestResult testParticleClassWithSideEffect(T particle, U sideEffect, AtomMetadata metadata) {
		final List<Result> results = particleClassWithSideEffectConstraints.stream()
			.filter(p -> p.getFirst().getFirst().isAssignableFrom(particle.getClass()))
			.filter(p -> p.getFirst().getSecond().isAssignableFrom(sideEffect.getClass()))
			.map(Pair::getSecond)
			.map(constraint -> ((ParticleClassWithSideEffectConstraintCheck<T, U>) constraint).check(particle, sideEffect, metadata))
			.collect(Collectors.toList());

		return new TestResult(results);
	}

	private void completePendingFungibleTransition() {
		if (pendingFungibleTransition != null) {
			fungibleTransitions.add(pendingFungibleTransition.build());
			pendingFungibleTransition = null;
		}
	}

	/**
	 * Mimics a fungible constraint check call
	 * @param input The input
	 * @param output The output
	 * @param metadata Atom metadata
	 * @return Result of the fungible transitions check
	 */
	public TestResult testFungible(Particle input, Particle output, AtomMetadata metadata) {
		completePendingFungibleTransition();

		return new TestResult(Collections.singletonList(
			new FungibleTransitionConstraintCheck(this.fungibleTransitions).validateParticles(
				Collections.singletonList(input), Collections.singletonList(output), metadata)));
	}

	/**
	 * Mimics a fungible constraint check call
	 * @param inputs The inputs
	 * @param output The output
	 * @param metadata Atom metadata
	 * @return Result of the fungible transitions check
	 */
	public TestResult testFungible(List<Particle> inputs, Particle output, AtomMetadata metadata) {
		completePendingFungibleTransition();

		return new TestResult(Collections.singletonList(
			new FungibleTransitionConstraintCheck(this.fungibleTransitions).validateParticles(
				inputs, Collections.singletonList(output), metadata)));
	}
}
