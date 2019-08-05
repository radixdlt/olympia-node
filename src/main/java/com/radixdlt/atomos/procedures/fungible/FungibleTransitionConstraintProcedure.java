package com.radixdlt.atomos.procedures.fungible;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radixdlt.atomos.AtomOS.ParticleClassWithSideEffectConstraintCheck;
import com.radixdlt.atomos.FungibleTransition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.procedures.fungible.FungibleTransitionConstraintCheck.FungibleValidationResult;
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
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Low-level implementation of fungible transition constraints.
 */
public class FungibleTransitionConstraintProcedure implements ConstraintProcedure {
	private final ImmutableSet<Class<? extends Particle>> inputTypes;
	private final ImmutableSet<Class<? extends Particle>> outputTypes;
	private final FungibleTransitionConstraintCheck check;
	private final ParticleValueMapper valueMapper;
	private final ImmutableList<FungibleTransition<?>> initialRequireWithChecks;

	public FungibleTransitionConstraintProcedure(Map<Class<? extends Particle>, FungibleTransition<? extends Particle>> transitions) {
		Objects.requireNonNull(transitions);

		List<FungibleTransition<? extends Particle>> fungibleTransitions = transitions.entrySet().stream()
			.map(Entry::getValue)
			.collect(Collectors.toList());

		this.check = new FungibleTransitionConstraintCheck(fungibleTransitions);

		this.initialRequireWithChecks =
			fungibleTransitions.stream()
				.filter(t -> t.getInitialWithConstraint() != null)
				.collect(ImmutableList.toImmutableList());

		this.valueMapper = ParticleValueMapper.from(fungibleTransitions);
		this.inputTypes = ImmutableSet.copyOf(fungibleTransitions.stream()
			.flatMap(ft -> ft.getAllInputs().stream())
			.collect(Collectors.toSet()));
		this.outputTypes = ImmutableSet.copyOf(fungibleTransitions.stream()
			.map(FungibleTransition::getOutputParticleClass)
			.collect(Collectors.toSet()));

		checkAllTypesKnown(inputTypes, outputTypes);
	}

	private static void checkAllTypesKnown(ImmutableSet<Class<? extends Particle>> inputTypes, ImmutableSet<Class<? extends Particle>> outputTypes) {
		List<Class<? extends Particle>> foreignTypes = inputTypes.stream()
			.filter(inputType -> !outputTypes.contains(inputType))
			.collect(Collectors.toList());

		if (!foreignTypes.isEmpty()) {
			throw new IllegalArgumentException("Foreign input types " + inputTypes + ", must be defined as fungibles");
		}
	}

	// FIXME: very bad method signature here but good enough for now without an overly big refactor
	private Stream<ProcedureError> checkRequireWith(ParticleGroup particleGroup, List<Particle> initParticles, AtomMetadata metadata) {
		return this.initialRequireWithChecks.stream().flatMap(check -> {
			Class<? extends Particle> particleClass = check.getOutputParticleClass();
			Class<? extends Particle> withParticleClass = check.getInitialWithConstraint().getFirst();
			ParticleClassWithSideEffectConstraintCheck<Particle, Particle> constraintCheck
				= (ParticleClassWithSideEffectConstraintCheck<Particle, Particle>) check.getInitialWithConstraint().getSecond();

			// Copy from ParticleClassWithSideEffectsProcedure
			final List<Particle> particles = initParticles.stream()
				.filter(particleClass::isInstance)
				.map(particleClass::cast)
				.collect(Collectors.toList());

			final List<Particle> sideEffects = ImmutableList.copyOf(
				particleGroup.particles(withParticleClass, Spin.UP).collect(Collectors.toList()));
			final List<Particle> unconsumedSideEffects = Lists.newArrayList(sideEffects);
			final Map<Particle, List<Pair<Particle, Result>>> unsatisfiedParticles = new HashMap<>();

			for (Particle particle : particles) {
				List<Pair<Particle, Result>> sideEffectResults = unconsumedSideEffects.stream()
					.map(sideEffect -> Pair.of(sideEffect, constraintCheck.check(particle, sideEffect, metadata)))
					.collect(Collectors.toList());
				Optional<Pair<Particle, Result>> approvedSideEffect = sideEffectResults.stream()
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
				return Stream.of(
					ProcedureError.of(String.format(
						"Particle class %s with side effect %s requirement violated:%n\tUnsatisfied Particles:%n%s%n",
						particleClass,
						withParticleClass,
						unsatisfiedParticles.entrySet().stream()
							.map(unsatisfied -> String.format("\t\t -> %s considered%n%s",
								unsatisfied.getKey().getHID(),
								unsatisfied.getValue().stream()
									.map(considered -> String.format("\t\t\t%s: '%s'",
										considered.getFirst().getHID(),
										considered.getSecond().getErrorMessage().get()))
									.collect(Collectors.joining(System.lineSeparator()))))
							.collect(Collectors.joining(System.lineSeparator()))
					))
				);
			}

			return Stream.empty();
		});
	}

	@Override
	public Stream<ProcedureError> validate(ParticleGroup group, AtomMetadata metadata) {
		Stream<Fungible> inputs = getFungibles(group, Spin.DOWN, this.inputTypes, valueMapper);
		Stream<Fungible> outputs = getFungibles(group, Spin.UP, this.outputTypes, valueMapper);

		FungibleValidationResult result = check.validate(inputs, outputs, metadata);

		Stream<ProcedureError> mapperIssues = result.getResult().errorStream().map(ProcedureError::of);

		List<Particle> initParticles = result.getMatchResults().stream()
			.flatMap(m -> m.getMatch().getMatchedInitials().fungibles())
			.map(Fungible::getParticle)
			.distinct()
			.collect(Collectors.toList());
		Stream<ProcedureError> initIssues = checkRequireWith(group, initParticles, metadata);

		return Stream.concat(mapperIssues, initIssues);
	}

	// @PackageLocalForTest
	ImmutableSet<Class<? extends Particle>> getInputTypes() {
		return this.inputTypes;
	}

	// @PackageLocalForTest
	ImmutableSet<Class<? extends Particle>> getOutputTypes() {
		return this.outputTypes;
	}

	/**
	 * Get relevant fungibles out of a particle group in the correct order as indexed by the {@link ParticleGroup}
	 *
	 * @param group         The group
	 * @param spin          The spin of interest
	 * @param relevantTypes The types of interest
	 * @return Relevant fungibles
	 */
	// @PackageLocalForTest
	static Stream<Fungible> getFungibles(ParticleGroup group,
	                                     Spin spin,
	                                     Set<Class<? extends Particle>> relevantTypes,
	                                     ParticleValueMapper valueMapper) {
		return group.spunParticles()
			.filter(spun -> spun.getSpin() == spin)
			.filter(spun -> relevantTypes.stream().anyMatch(type -> type.isAssignableFrom(spun.getParticle().getClass())))
			.map(spun -> new Fungible(
				spun.getParticle(),
				spun.getParticle().getClass(),
				valueMapper.amount(spun.getParticle()),
				group.indexOfSpunParticle(spun))
			);
	}

}