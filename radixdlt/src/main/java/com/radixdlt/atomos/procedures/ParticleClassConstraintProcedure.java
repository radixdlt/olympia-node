package com.radixdlt.atomos.procedures;

import com.google.common.collect.Streams;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import com.radixdlt.constraintmachine.ConstraintProcedure;
import com.radixdlt.constraintmachine.ProcedureError;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.Spin;

import java.util.Objects;

/**
 * Low level code for Constraints based on just the Particle class.
 *
 * @param <T> particle class of the particle constraint
 */
public final class ParticleClassConstraintProcedure<T extends Particle> implements ConstraintProcedure {
	private final Class<T> particleClass;
	private final BiFunction<T, AtomMetadata, Result> constraintCheck;

	public ParticleClassConstraintProcedure(Class<T> particleClass, BiFunction<T, AtomMetadata, Result> constraintCheck) {
		this.particleClass = Objects.requireNonNull(particleClass);
		this.constraintCheck = Objects.requireNonNull(constraintCheck);
	}

	@Override
	public Stream<ProcedureError> validate(ParticleGroup group, AtomMetadata metadata) {
		return Streams.mapWithIndex(
			group.particles(particleClass, Spin.UP),
			(particle, i) -> constraintCheck.apply(particle, metadata)
				.errorStream()
				.map(err -> ProcedureError.of(group, err, i))
			).flatMap(l -> l);
	}
}
