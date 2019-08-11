package com.radixdlt.atomos;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Exposes the interface which application particle constraints/transitions can be programmed against.
 */
public interface SysCalls {

	/**
	 * Registers a Particle with a given identifier.
	 * This is required for all other system calls using the particle.
	 * @param particleClass The particle class
	 * @param mapper Mapping to a destination the particle will be stored in
	 */
	<T extends Particle> void registerParticle(
		Class<T> particleClass,
		Function<T, RadixAddress> mapper,
		Function<T, Result> staticCheck
	);

	/**
	 * Registers a Particle with a given identifier.
	 * This is required for all other system calls using the particle.
	 * @param particleClass The particle class
	 * @param mapper Mapping to the destinations a particle will be stored in
	 */
	<T extends Particle> void registerParticleMultipleAddress(
		Class<T> particleClass,
		Function<T, Set<RadixAddress>> mapper,
		Function<T, Result> staticCheck
	);

	<T extends Particle, U extends Particle> void createTransition(
		Class<T> inputClass,
		Class<U> outputClass,
		TransitionProcedure<T, U> procedure
	);

	/**
	 * Creates a new resource type based on a particle. The resource type can be allocated by consuming
	 * an RRI which then becomes the resource's global identifier.
	 */
	<T extends Particle> void createTransitionFromRRI(
		Class<T> outputClass,
		Function<T, RRI> rriMapper
	);

	/**
	 * Creates a new resource type based on two particles. The resource type can be allocated by consuming
	 * an RRI which then becomes the resource's global identifier.
	 */
	<T extends Particle, U extends Particle> void createTransitionFromRRICombined(
		Class<T> outputClass0,
		Function<T, RRI> rriMapper0,
		Class<U> outputClass1,
		Function<U, RRI> rriMapper1,
		BiPredicate<T, U> combinedCheck
	);
}
