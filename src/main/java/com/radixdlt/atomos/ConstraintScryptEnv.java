package com.radixdlt.atomos;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.procedures.CombinedTransition;
import com.radixdlt.constraintmachine.OutputProcedure;
import com.radixdlt.constraintmachine.OutputProcedure.OutputProcedureResult;
import com.radixdlt.constraintmachine.OutputWitnessValidator;
import com.radixdlt.constraintmachine.OutputWitnessValidator.OutputWitnessValidatorResult;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.TransitionId;
import com.radixdlt.utils.Pair;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.CMAction;
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
	private final ImmutableMap<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions;
	private final Function<RadixAddress, Result> addressChecker;

	private final Map<Class<? extends Particle>, ParticleDefinition<Particle>> scryptParticleDefinitions;
	private final Map<TransitionId, TransitionProcedure<Particle, Particle>> scryptTransitionProcedures;
	private final Map<Pair<Class<? extends Particle>, Class<? extends Particle>>, WitnessValidator<Particle, Particle>> scryptWitnessValidators;

	ConstraintScryptEnv(
		ImmutableMap<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions,
		Function<RadixAddress, Result> addressChecker
	) {
		this.particleDefinitions = particleDefinitions;
		this.addressChecker = addressChecker;

		this.scryptParticleDefinitions = new HashMap<>();
		this.scryptTransitionProcedures = new HashMap<>();
		this.scryptWitnessValidators = new HashMap<>();
	}

	public Map<Class<? extends Particle>, ParticleDefinition<Particle>> getScryptParticleDefinitions() {
		return scryptParticleDefinitions;
	}

	public Map<TransitionId, TransitionProcedure<Particle, Particle>> getScryptTransitionProcedures() {
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

	private <T extends Particle> boolean particleDefinitionExists(Class<T> particleClass) {
		return particleDefinitions.containsKey(particleClass) || scryptParticleDefinitions.containsKey(particleClass);
	}

	private <T extends Particle> ParticleDefinition<Particle> getParticleDefinition(Class<T> particleClass) {
		ParticleDefinition<Particle> particleDefinition = particleDefinitions.get(particleClass);
		if (particleDefinition != null) {
			if (!particleDefinition.allowsTransitionsFromOutsideScrypts()) {
				throw new IllegalStateException(particleClass + " can only be used in registering scrypt.");
			}
			return particleDefinition;
		}

		particleDefinition = scryptParticleDefinitions.get(particleClass);
		if (particleDefinition == null) {
			throw new IllegalStateException(particleClass + " is not registered.");
		}

		return particleDefinition;
	}

	@Override
	public <T extends Particle> void registerParticleMultipleAddresses(
		Class<T> particleClass,
		Function<T, Set<RadixAddress>> mapper,
		Function<T, Result> staticCheck,
		Function<T, RRI> rriMapper
	) {
		if (particleDefinitionExists(particleClass)) {
			throw new IllegalStateException("Particle " + particleClass + " is already registered");
		}

		scryptParticleDefinitions.put(particleClass, new ParticleDefinition<>(
			p -> mapper.apply((T) p).stream(),
			p -> {
				if (rriMapper != null) {
					final RRI rri = rriMapper.apply((T) p);
					if (rri == null) {
						return Result.error("rri cannot be null");
					}

					final Result rriAddressResult = addressChecker.apply(rri.getAddress());
					if (rriAddressResult.isError()) {
						return rriAddressResult;
					}
				}

				final Set<RadixAddress> addresses = mapper.apply((T) p);
				if (addresses.isEmpty()) {
					return Result.error("address required");
				}

				for (RadixAddress address : addresses) {
					Result addressResult = addressChecker.apply(address);
					if (addressResult.isError()) {
						return addressResult;
					}
				}

				return staticCheck.apply((T) p);
			},
			rriMapper == null ? null : p -> rriMapper.apply((T) p),
			false
		));
	}

	@Override
	public <T extends Particle> void createTransitionFromRRI(Class<T> particleClass) {
		ParticleDefinition<Particle> particleDefinition = getParticleDefinition(particleClass);
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
		final ParticleDefinition<Particle> particleDefinition0 = getParticleDefinition(particleClass0);
		if (particleDefinition0.getRriMapper() == null) {
			throw new IllegalStateException(particleClass0 + " must be registered with an RRI mapper.");
		}
		final ParticleDefinition<Particle> particleDefinition1 = getParticleDefinition(particleClass1);
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

	@Override
	public <T extends Particle> void createOutputOnlyTransition(
		Class<T> outputClass,
		OutputProcedure<T> procedure,
		OutputWitnessValidator<T> witnessValidator
	) {
		this.createTransitionInternal(
			null,
			outputClass,
			(in, inUsed, out, outUsed) -> {
				OutputProcedureResult res = procedure.execute(out);
				return res.isSuccess() ? ProcedureResult.popOutput(null) : ProcedureResult.error(res.getErrorMessage());
			},
			(res, in, out, witness) -> {
				OutputWitnessValidatorResult witnessRes = witnessValidator.validate(out, witness);
				return witnessRes.isSuccess() ? WitnessValidatorResult.success() : WitnessValidatorResult.error(witnessRes.getErrorMessage());
			}
		);
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
		final ParticleDefinition<Particle> inputDefinition = inputClass != null ? getParticleDefinition(inputClass) : null;
		final ParticleDefinition<Particle> outputDefinition = outputClass != null ? getParticleDefinition(outputClass) : null;

		final TransitionProcedure<Particle, Particle> transformedProcedure;

		// RRIs must be the same across RRI particle transitions
		if (inputClass != null && inputDefinition.getRriMapper() != null
			&& outputClass != null && outputDefinition.getRriMapper() != null) {
			transformedProcedure = (in, inUsed, out, outUsed) -> {
				final RRI inputRRI = inputDefinition.getRriMapper().apply(in);
				final RRI outputRRI = outputDefinition.getRriMapper().apply(out);
				if (!inputRRI.equals(outputRRI)) {
					return ProcedureResult.error("Input/Output RRIs not equal");
				}

				return procedure.execute((T) in, inUsed, (U) out, outUsed);
			};
		} else {
			transformedProcedure = toGeneric(procedure);
		}

		final TransitionId transitionId = new TransitionId(inputClass, null, outputClass, null);
		scryptTransitionProcedures.put(transitionId, transformedProcedure);
		scryptWitnessValidators.put(Pair.of(inputClass, outputClass), toGeneric(witnessValidator));
	}
}
