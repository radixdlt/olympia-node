package com.radixdlt.atomos;

import com.radixdlt.atomos.mapper.ParticleToAmountMapper;
import com.radixdlt.atomos.mapper.ParticleToRRIMapper;
import com.radixdlt.atomos.mapper.ParticleToShardableMapper;
import com.radixdlt.atomos.mapper.ParticleToShardablesMapper;
import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.AtomMetadata;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Exposes the interface which application particle constraints can be built on top of.
 */
public interface AtomOS {
	/**
	 * Registers a Particle with a given identifier.
	 * This is required for all other system calls using the particle.
	 * @param particleClass The particle class
	 * @param name The name identifiying the particle
	 * @param mapper Mapping to the destinations a particle will be stored in
	 */
	<T extends Particle> void registerParticle(Class<T> particleClass, String name, ParticleToShardablesMapper<T> mapper);

	/**
	 * Registers a Particle with a given identifier.
	 * This is required for all other system calls using the particle.
	 * @param particleClass The particle class
	 * @param name The name identifiying the particle
	 * @param mapper Mapping to a destination the particle will be stored in
	 */
	<T extends Particle> void registerParticle(Class<T> particleClass, String name, ParticleToShardableMapper<T> mapper);

	/**
	 * System call endpoint which allows an atom model application to program constraints
	 * against a particle class and an getDestinations over that class.
	 *
	 * This endpoint returns a callback on which the application must define the constraint.
	 * This function MUST be a pure function (i.e. no states)
	 *
	 * @param particleClass particle class to add a constraint to
	 * @param indexer getDestinations function
	 * @param <T> particle class to add a constraint to
	 * @return a callback function onto which the constraint will be defined
	 */
	<T extends Particle> IndexedConstraint<T> onIndexed(Class<T> particleClass, ParticleToRRIMapper<T> indexer);

	/**
	 * System call endpoint which allows an atom model application to program constraints
	 * against a fungible particle class and its transitions.
	 *
	 * This endpoint returns a callback on which the application must define the constraint.
	 * This function MUST be a pure function (i.e. no states).
	 *
	 * @param particleClass particle class to add constraint to
	 * @param particleToAmountMapper mapper from instance of particle class to its fungible amount
	 * @param <T> type of particle class to add a constraint to
	 * @return a callback function onto which the constraint will be defined
	 */
	<T extends Particle> FungibleTransitionConstraintStub<T> onFungible(
		Class<T> particleClass,
		ParticleToAmountMapper<T> particleToAmountMapper,
		BiFunction<T, T, Boolean> fungibleEquals
	);

	<T extends Particle> PayloadParticleClassConstraint<T> onPayload(Class<T> particleClass);

	/**
	 * System call endpoint which allows an atom model application to program constraints
	 * against an instance of a particle with a given particle class (i.e. a stateless check
	 * just the particle itself).
	 *
	 * This endpoint returns a callback on which the application must define the constraint.
	 * This function MUST be a pure function (i.e. no states)
	 *
	 * @param <T> particle class to add a constraint to
	 * @param particleClass particle class to add a constraint to
	 * @return a callback function onto which the constraint will be defined
	 */
	default <T extends Particle> ParticleClassConstraint<T> on(Class<T> particleClass) {
		return check -> { };
	}

	/**
	 * Actual function which returns result of an indexed constraint given a state
	 */
	interface IndexedConstraintCheck<T extends Particle> extends BiFunction<T, AtomMetadata, Result> {
	}

	interface InitializedIndexedConstraint<T extends Particle> {
		/**
		 * Adds a constraint check for a side effect that is required for this particle
		 * @param sideEffectClass The particle class of the required side effect
		 * @param constraint The constraint check for the required side effect
		 * @param <U> The type of the required side effect
		 */
		<U extends Particle> void requireInitialWith(Class<U> sideEffectClass, ParticleClassWithSideEffectConstraintCheck<T, U> constraint);
	}

	/**
	 * Callback for an implementation of a constraint based on an indexed particle group.
	 * This interface should not need to be implemented by application layer.
	 *
	 * @param <T> the type of particle
	 */
	interface IndexedConstraint<T extends Particle> {
		InitializedIndexedConstraint<T> requireInitial(IndexedConstraintCheck<T> constraint);
	}

	/**
	 * Callback for defining an implementation of a constraint based on a particle class and a dependency.
	 * Helper for defining particle class constraints that can have dependencies.
	 */
	interface ParticleRequireWithStub<T extends Particle> {
		/**
		 * Defines a requirement for this particle.
		 */
		default <U extends Particle> void requireWith(Class<U> sideEffectClass, ParticleClassWithSideEffectConstraintCheck<T, U> constraint) {
		}
	}

	interface PayloadParticleClassConstraint<T extends Particle> {
		/**
		 * Adds a constraint check for this particle class
		 * @param constraint the constraint check
		 */
		void require(BiFunction<T, AtomMetadata, Result> constraint);
	}


	/**
	 * Callback for an implementation of a constraint based on a particle class.
	 * This interface should not need to be implemented by application layer.
	 *
	 * @param <T> the type of particle
	 */
	interface ParticleClassConstraint<T extends Particle> {
		/**
		 * Adds a constraint check for this particle class that ignores metadata
		 * @param constraint the constraint check
		 */
		void require(Function<T, Result> constraint);
	}

	@FunctionalInterface
	interface ParticleClassWithDependenceConstraintCheck<T extends Particle, U extends Particle> {
		Result check(List<T> nextState, U dependency, AtomMetadata meta);
	}

	@FunctionalInterface
	interface ParticleClassWithDependencyConstraint<T extends Particle, U extends Particle> {
		void require(ParticleClassWithDependenceConstraintCheck<T, U> constraint);
	}

	@FunctionalInterface
	interface ParticleClassWithSideEffectConstraintCheck<T extends Particle, U extends Particle> {
		Result check(T particle, U sideEffect, AtomMetadata meta);
	}

	/**
	 * Actual function which returns result of a single from particle of the fungible transition
	 * @param <T>
	 */
	@FunctionalInterface
	interface FungibleTransitionInputConstraint<T extends Particle, U extends Particle> {
		/**
		 * Check whether a certain transition *from* the given Particle *to* a given Particle is valid
		 * Note: Amounts should not be considered at this place, since they are taken into account elsewhere.
		 * @param fromParticle The particle we transition from
		 * @param toParticle The particle we transition to
		 * @param metadata The metadata of the containing Atom
		 * @return A {@link Result} of the check
		 */
		Result apply(T fromParticle, U toParticle, AtomMetadata metadata);
	}

	/**
	 * Actual function which returns result of a single from particle of the fungible transition
	 * @param <T>
	 */
	@FunctionalInterface
	interface FungibleTransitionInitialConstraint<T extends Particle> {
		/**
		 * Check whether a certain transition from nothing *to* a given Particle is valid
		 * Note: Amounts should not be considered at this place, since they are taken into account elsewhere.
		 * @param toParticle The particle we transition to
		 * @param metadata The metadata of the containing Atom
		 * @return A {@link Result} of the check
		 */
		Result apply(T toParticle, AtomMetadata metadata);
	}

	/**
	 * Callback stub for defining the kind of a fungible transition, either initial or with input.
	 * Returns a {@link FungibleTransitionConstraint} callback where further formulas can be defined.
	 * @param <T>
	 */
	interface FungibleTransitionConstraintStub<T extends Particle> {
		/**
		 * Define an 'initial' state transition dependent on another particle state.
		 * FIXME: this is very obviously a bad interface due to it not working on the fungible level
		 * FIXME: but good enough for now without a big refactor of the fungible system
		 * @return self, the callback to define further constraints
		 */
		 <U extends Particle> FungibleTransitionConstraint<T> requireInitialWith(Class<U> sideEffectClass,
			 ParticleClassWithSideEffectConstraintCheck<T, U> constraint);

		/**
		 * Define a formula of this transition.
		 *
		 * @return self, the callback to define further constraints
		 */
		FungibleTransitionConstraint<T> requireFrom(FungibleFormula formula);

		/**
		 * Utility method to define a formula of this transition with a single input.
		 * Note: For more complicated formulas, use requireFrom(FungibleFormula) directly.
		 *
		 * @return self, the callback to define further constraints
		 */
		default <U extends Particle> FungibleTransitionConstraint<T> requireFrom(long amount1, Class<U> cls1,
			FungibleTransitionInputConstraint<U, T> check) {
			return requireFrom(FungibleFormula.from(Stream.of(
					new FungibleTransitionMember<>(cls1, check)
				),
				FungibleComposition.of(amount1, cls1)
			));
		}
	}

	/**
	 * Callback for an implementation of a fungible transition constraint.
	 * A fungible transition is a transition from one set of fungibles to another given a certain *formula*.
	 * A fungible transition consists of multiple formulas, each defining its own constraints and compositions.
	 *
	 * @param <T> the type of Particle
	 */
	interface FungibleTransitionConstraint<T extends Particle> {
		/**
		 * Define a formula of this transition.
		 *
		 * @return self, the callback to define further constraints
		 */
		FungibleTransitionConstraint<T> orFrom(FungibleFormula formula);

		/**
		 * Utility method to define a formula of this transition with a single input.
		 * Note: For more complicated formulas, use requireFrom(FungibleFormula) directly.
		 *
		 * @return self, the callback to define further constraints
		 */
		default <U extends Particle> FungibleTransitionConstraint<T> orFrom(long amount1, Class<U> cls1,
			FungibleTransitionInputConstraint<U, T> check) {
			return orFrom(FungibleFormula.from(Stream.of(
					new FungibleTransitionMember<>(cls1, check)
				),
				FungibleComposition.of(amount1, cls1)
			));
		}
	}

}
