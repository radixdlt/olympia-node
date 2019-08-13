package com.radixdlt.atomos;

import com.radixdlt.atomos.mapper.ParticleToAmountMapper;
import com.radixdlt.atomos.mapper.ParticleToRRIMapper;
import com.radixdlt.atomos.mapper.ParticleToShardableMapper;
import com.radixdlt.atomos.mapper.ParticleToShardablesMapper;
import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.AtomMetadata;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Exposes the interface which application particle constraints can be built on top of.
 */
public interface SysCalls {
	/**
	 * Registers a Particle with a given identifier.
	 * This is required for all other system calls using the particle.
	 * @param particleClass The particle class
	 * @param mapper Mapping to the destinations a particle will be stored in
	 */
	<T extends Particle> void registerParticle(Class<T> particleClass, ParticleToShardablesMapper<T> mapper);

	/**
	 * Registers a Particle with a given identifier.
	 * This is required for all other system calls using the particle.
	 * @param particleClass The particle class
	 * @param mapper Mapping to a destination the particle will be stored in
	 */
	<T extends Particle> void registerParticle(Class<T> particleClass, ParticleToShardableMapper<T> mapper);


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
	 * Creates a new resource type based on a particle. The resource type can be allocated by consuming
	 * an RRI which then becomes the resource's global identifier.
	 */
	<T extends Particle> void newResourceType(
		Class<T> particleClass,
		ParticleToRRIMapper<T> indexer
	);

	/**
	 * Creates a new resource type based on two particles. The resource type can be allocated by consuming
	 * an RRI which then becomes the resource's global identifier.
	 */
	<T extends Particle, U extends Particle> void newResourceType(
		Class<T> particleClass0,
		ParticleToRRIMapper<T> indexer0,
		Class<U> particleClass1,
		ParticleToRRIMapper<U> indexer1,
		BiPredicate<T, U> combinedResource
	);

	/**
	 * Creates a new transitionless particle type.
	 */
	<T extends Particle> TransitionlessParticleClassConstraint<T> onTransitionless(Class<T> particleClass);

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
	<T extends Particle> FungibleTransitionConstraint<T> onFungible(
		Class<T> particleClass,
		ParticleToAmountMapper<T> particleToAmountMapper
	);

	interface TransitionlessParticleClassConstraint<T extends Particle> {
		/**
		 * Adds a constraint check for this particle class
		 * @param witnessValidator the constraint check
		 */
		void require(WitnessValidator<T> witnessValidator);
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
	interface WitnessValidator<T extends Particle> {
		/**
		 * @param fromParticle The particle we transition from
		 * @param metadata The metadata of the containing Atom
		 * @return A {@link Result} of the check
		 */
		Result validate(T fromParticle, AtomMetadata metadata);
	}

	/**
	 * Callback for an implementation of a fungible transition constraint.
	 *
	 * @param <T> the type of Particle
	 */
	interface FungibleTransitionConstraint<T extends Particle> {
		<U extends Particle> FungibleTransitionConstraint<T> transitionTo(
			Class<U> outputClass,
			BiPredicate<T, U> fungibleValidator,
			WitnessValidator<T> witnessValidator
		);
	}
}
