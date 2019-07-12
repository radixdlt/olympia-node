package com.radixdlt.atomos.procedures;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.radixdlt.atomos.AtomOS.ParticleClassWithSideEffectConstraintCheck;
import com.radixdlt.atomos.Result;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.Spin;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ConstraintProcedure;
import com.radixdlt.constraintmachine.ProcedureError;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Low-level implementation of a particle class with side effect constraint.
 * @param <T> The type of the particle
 * @param <U> The type of the side effect
 */
public class ParticleClassWithSideEffectConstraintProcedure<T extends Particle, U extends Particle> implements ConstraintProcedure {
	private final Class<T> particleClass;
	private final Class<U> sideEffectClass;
	private final ParticleClassWithSideEffectConstraintCheck<T, U> constraintCheck;

	public ParticleClassWithSideEffectConstraintProcedure(Class<T> particleClass, Class<U> sideEffectClass, ParticleClassWithSideEffectConstraintCheck<T, U> constraintCheck) {
		this.particleClass = Objects.requireNonNull(particleClass, "particleClass is required");
		this.sideEffectClass = Objects.requireNonNull(sideEffectClass, "sideEffectClass is required");
		this.constraintCheck = Objects.requireNonNull(constraintCheck, "constraintCheck is required");
	}

	@Override
	public Stream<ProcedureError> validate(ParticleGroup group, AtomMetadata metadata) {
		final List<T> particles = group.particles(particleClass, Spin.UP).collect(ImmutableList.toImmutableList());
		final List<U> sideEffects = group.particles(sideEffectClass, Spin.UP).collect(ImmutableList.toImmutableList());
		final List<U> unconsumedSideEffects = Lists.newArrayList(sideEffects);
		final Map<T, List<Pair<U, Result>>> unsatisfiedParticles = new HashMap<>();

		for (T particle : particles) {
			List<Pair<U, Result>> sideEffectResults = unconsumedSideEffects.stream()
				.map(sideEffect -> Pair.of(sideEffect, constraintCheck.check(particle, sideEffect, metadata)))
				.collect(Collectors.toList());
			Optional<Pair<U, Result>> approvedSideEffect = sideEffectResults.stream()
				.filter(p -> p.getSecond().isSuccess())
				.findFirst();

			if (!approvedSideEffect.isPresent()) {
				unsatisfiedParticles.put(particle, sideEffectResults.stream()
					.filter(p -> p.getSecond().isError())
					.collect(Collectors.toList()));
			} else {
				unconsumedSideEffects.remove(approvedSideEffect.get().getFirst());
			}
		}

		if (!unsatisfiedParticles.isEmpty()) {
			return Stream.of(ProcedureError.of(
				String.format(
					"Particle class %s with side effect %s requirement violated:%n\tUnsatisfied Particles:%n%s%n",
					particleClass,
					sideEffectClass,
					unsatisfiedParticles.entrySet().stream()
						.map(unsatisfied -> String.format("\t\t -> %s considered%n%s",
							unsatisfied.getKey().getHID(),
							unsatisfied.getValue().stream()
								.map(considered -> String.format("\t\t\t%s: '%s'",
									considered.getFirst().getHID(),
									considered.getSecond().getErrorMessage().get()))
								.collect(Collectors.joining(System.lineSeparator()))))
						.collect(Collectors.joining(System.lineSeparator()))
				)
			));
		}

		return Stream.empty();
	}
}
