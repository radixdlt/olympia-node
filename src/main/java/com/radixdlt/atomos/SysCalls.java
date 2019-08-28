package com.radixdlt.atomos;

import com.google.common.reflect.TypeToken;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.UsedData;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The interface in which a constraint scrypt can be programmed against.
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
	 * @param mapper Mapping to a destination the particle will be stored in
	 */
	<T extends Particle> void registerParticle(
		Class<T> particleClass,
		Function<T, RadixAddress> mapper,
		Function<T, Result> staticCheck,
		Function<T, RRI> rriMapper
	);

	/**
	 * Registers a Particle with a given identifier.
	 * This is required for all other system calls using the particle.
	 * @param particleClass The particle class
	 * @param mapper Mapping to the destinations a particle will be stored in
	 */
	<T extends Particle> void registerParticleMultipleAddresses(
		Class<T> particleClass,
		Function<T, Set<RadixAddress>> mapper,
		Function<T, Result> staticCheck
	);

	/**
	 * Registers a Particle with a given identifier.
	 * This is required for all other system calls using the particle.
	 * @param particleClass The particle class
	 * @param mapper Mapping to the destinations a particle will be stored in
	 */
	<T extends Particle> void registerParticleMultipleAddresses(
		Class<T> particleClass,
		Function<T, Set<RadixAddress>> mapper,
		Function<T, Result> staticCheck,
		Function<T, RRI> rriMapper
	);

	/**
	 * Defines a valid transition in the constraint machine as well as the
	 * requirements for executing that transition.
	 *
	 * @param inputClass class of the input particle
	 * @param outputClass class of the output particle
	 * @param procedure procedure which gets executed on the constraint machine
	 * @param <I> input particle type
	 * @param <U> output particle type
	 */
	<I extends Particle, N extends UsedData, O extends Particle, U extends UsedData> void createTransition(
		Class<I> inputClass,
		TypeToken<N> inputUsedClass,
		Class<O> outputClass,
		TypeToken<U> outputUsedClass,
		TransitionProcedure<I, N, O, U> procedure
	);

	/**
	 * Creates a new resource globally identifiable by an RRI.
	 * @param outputClass particle to be creating from RRI must be a particle registered as rri capable
	 */
	<T extends Particle> void createTransitionFromRRI(Class<T> outputClass);

	/**
	 * Creates a new resource globally identifiable by an RRI.
	 * @param outputClass0 primary particle to be created from RRI, must be a particle registered as rri capable
	 * @param outputClass1 secondary particle to be created from RRI, must be a particle registered as rri capable
	 */
	<T extends Particle, U extends Particle> void createTransitionFromRRICombined(
		Class<T> outputClass0,
		Class<U> outputClass1,
		BiFunction<T, U, Result> combinedCheck
	);
}
