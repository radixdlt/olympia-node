package com.radixdlt.atomos;

import com.radixdlt.atommodel.procedures.CombinedTransition;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionProcedure.CMAction;
import com.radixdlt.constraintmachine.TransitionProcedure.ProcedureResult;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * SysCall environment for CMAtomOS Constraint Scrypts.
 */
final class ConstraintScryptEnv implements SysCalls {
	private final Map<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions;
	private final Map<Class<? extends Particle>, ParticleDefinition<Particle>> scryptParticleDefinitions;
	private final Map<Pair<Class<? extends Particle>, Class<? extends Particle>>, TransitionProcedure<Particle, Particle>> scryptTransitionProcedures;
	private final Map<Pair<Class<? extends Particle>, Class<? extends Particle>>, WitnessValidator<Particle, Particle>> scryptWitnessValidators;

	ConstraintScryptEnv(
		Map<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions,
		Map<Class<? extends Particle>, ParticleDefinition<Particle>> scryptParticleDefinitions
	) {
		this.particleDefinitions = particleDefinitions;
		this.scryptParticleDefinitions = scryptParticleDefinitions;
		this.scryptTransitionProcedures = new HashMap<>();
		this.scryptWitnessValidators = new HashMap<>();
	}

	public Map<Pair<Class<? extends Particle>, Class<? extends Particle>>, TransitionProcedure<Particle, Particle>> getScryptTransitionProcedures() {
		return scryptTransitionProcedures;
	}

	public Map<Pair<Class<? extends Particle>, Class<? extends Particle>>, WitnessValidator<Particle, Particle>> getScryptWitnessValidators() {
		return scryptWitnessValidators;
	}

	@Override
	public <T extends Particle> void registerParticle(
		Class<T> particleClass,
		Function<T, RadixAddress> mapper,
		Function<T, Result> staticCheck
	) {
		registerParticleMultipleAddresses(
			particleClass,
			(T particle) -> Collections.singleton(mapper.apply(particle)),
			staticCheck
		);
	}

	@Override
	public <T extends Particle> void registerParticle(
		Class<T> particleClass,
		Function<T, RadixAddress> mapper,
		Function<T, Result> staticCheck,
		Function<T, RRI> rriMapper
	) {
		registerParticleMultipleAddresses(
			particleClass,
			(T particle) -> Collections.singleton(mapper.apply(particle)),
			staticCheck,
			rriMapper
		);
	}

	@Override
	public <T extends Particle> void registerParticleMultipleAddresses(
		Class<T> particleClass,
		Function<T, Set<RadixAddress>> mapper,
		Function<T, Result> staticCheck
	) {
		registerParticleMultipleAddresses(particleClass, mapper, staticCheck, null);
	}

	@Override
	public <T extends Particle> void registerParticleMultipleAddresses(
		Class<T> particleClass,
		Function<T, Set<RadixAddress>> mapper,
		Function<T, Result> staticCheck,
		Function<T, RRI> rriMapper
	) {
		if (particleDefinitions.containsKey(particleClass)) {
			throw new IllegalStateException("Particle " + particleClass + " is already registered");
		}

		scryptParticleDefinitions.put(particleClass, new ParticleDefinition<>(
			p -> mapper.apply((T) p).stream(),
			p -> {
				if (rriMapper != null) {
					if (rriMapper.apply((T) p) == null) {
						return Result.error("rri cannot be null");
					}
				}

				return staticCheck.apply((T) p);
			},
			rriMapper == null ? null : p -> rriMapper.apply((T) p)
		));
	}

	@Override
	public <T extends Particle> void createTransitionFromRRI(Class<T> particleClass) {
		final ParticleDefinition<Particle> particleDefinition = scryptParticleDefinitions.get(particleClass);
		if (particleDefinition == null) {
			throw new IllegalStateException(particleClass + " must be registered in calling scrypt.");
		}
		if (particleDefinition.getRriMapper() == null) {
			throw new IllegalStateException(particleClass + " must be registered with an RRI mapper.");
		}

		createTransitionInternal(
			RRIParticle.class,
			particleClass,
			(in, inUsed, out, outUsed) -> {
				if (inUsed != null || outUsed != null) {
					return ProcedureResult.error("Expecting RRI and output particle to be fully unused.");
				}

				return ProcedureResult.popInputOutput();
			},
			(res, in, out, meta) -> res == CMAction.POP_INPUT_OUTPUT && meta.isSignedBy(in.getRri().getAddress().getKey())
				? WitnessValidatorResult.success() : WitnessValidatorResult.error("Not signed by " + in.getRri().getAddress())
		);
	}

	@Override
	public <T extends Particle, U extends Particle> void createTransitionFromRRICombined(
		Class<T> particleClass0,
		Class<U> particleClass1,
		BiFunction<T, U, Result> combinedCheck
	) {
		final ParticleDefinition<Particle> particleDefinition0 = scryptParticleDefinitions.get(particleClass0);
		if (particleDefinition0 == null) {
			throw new IllegalStateException(particleClass0 + " must be registered in calling scrypt.");
		}
		if (particleDefinition0.getRriMapper() == null) {
			throw new IllegalStateException(particleClass0 + " must be registered with an RRI mapper.");
		}
		final ParticleDefinition<Particle> particleDefinition1 = scryptParticleDefinitions.get(particleClass1);
		if (particleDefinition1 == null) {
			throw new IllegalStateException(particleClass1 + " must be registered in calling scrypt.");
		}
		if (particleDefinition1.getRriMapper() == null) {
			throw new IllegalStateException(particleClass1 + " must be registered with an RRI mapper.");
		}

		final TransitionProcedure<RRIParticle, T> procedure0 = new CombinedTransition<>(
			particleClass1,
			combinedCheck
		);
		createTransitionInternal(
			RRIParticle.class,
			particleClass0,
			procedure0,
			(res, in, out, meta) -> {
				if (res == CMAction.POP_INPUT_OUTPUT) {
					if (!meta.isSignedBy(in.getRri().getAddress().getKey())) {
						return WitnessValidatorResult.error("Not signed by " + in.getRri().getAddress());
					}
				}

				return WitnessValidatorResult.success();
			}
		);

		final TransitionProcedure<RRIParticle, U> procedure1 = new CombinedTransition<>(
			particleClass0,
			(u, v) -> combinedCheck.apply(v, u)
		);
		createTransitionInternal(
			RRIParticle.class,
			particleClass1,
			procedure1,
			(res, in, out, meta) -> {
				if (res == CMAction.POP_INPUT_OUTPUT) {
					if (!meta.isSignedBy(in.getRri().getAddress().getKey())) {
						return WitnessValidatorResult.error("Not signed by " + in.getRri().getAddress());
					}
				}

				return WitnessValidatorResult.success();
			}
		);
	}

	@Override
	public <T extends Particle, U extends Particle> void createTransition(
		Class<T> inputClass,
		Class<U> outputClass,
		TransitionProcedure<T, U> procedure,
		WitnessValidator<T, U> witnessValidator
	) {
		createTransitionInternal(inputClass, outputClass, procedure, witnessValidator);
	}

	private static <T extends Particle, U extends Particle> TransitionProcedure<Particle, Particle> toGeneric(TransitionProcedure<T, U> procedure) {
		return (in, inUsed, out, outUsed) -> procedure.execute((T) in, inUsed, (U) out, outUsed);
	}

	private static <T extends Particle, U extends Particle> WitnessValidator<Particle, Particle> toGeneric(WitnessValidator<T, U> validator) {
		return (res, in, out, meta) -> validator.validate(res, (T) in, (U) out, meta);
	}

	private <T extends Particle, U extends Particle> void createTransitionInternal(
		Class<T> inputClass,
		Class<U> outputClass,
		TransitionProcedure<T, U> procedure,
		WitnessValidator<T, U> witnessValidator
	) {
		if ((inputClass != null && !scryptParticleDefinitions.containsKey(inputClass))) {
			throw new IllegalStateException(inputClass + " must be registered in calling scrypt.");
		}
		if (outputClass != null && !scryptParticleDefinitions.containsKey(outputClass)) {
			throw new IllegalStateException(outputClass + " must be registered in calling scrypt.");
		}

		final TransitionProcedure<Particle, Particle> transformedProcedure;

		// RRIs must be the same across RRI particle transitions
		if (inputClass != null && scryptParticleDefinitions.get(inputClass).getRriMapper() != null
			&& outputClass != null && scryptParticleDefinitions.get(outputClass).getRriMapper() != null) {
			transformedProcedure = (in, inUsed, out, outUsed) -> {
				final RRI inputRRI = scryptParticleDefinitions.get(inputClass).getRriMapper().apply(in);
				final RRI outputRRI = scryptParticleDefinitions.get(outputClass).getRriMapper().apply(out);
				if (!inputRRI.equals(outputRRI)) {
					return ProcedureResult.error("Input/Output RRIs not equal");
				}

				return procedure.execute((T) in, inUsed, (U) out, outUsed);
			};
		} else {
			transformedProcedure = toGeneric(procedure);
		}

		scryptTransitionProcedures.put(Pair.of(inputClass, outputClass), transformedProcedure);
		scryptWitnessValidators.put(Pair.of(inputClass, outputClass), toGeneric(witnessValidator));
	}
}
