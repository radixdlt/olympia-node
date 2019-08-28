package com.radixdlt.atomos;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import com.radixdlt.atommodel.procedures.CombinedTransition;
import com.radixdlt.atommodel.procedures.CombinedTransition.UsedParticle;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.UsedData;
import com.radixdlt.constraintmachine.VoidUsedData;
import com.radixdlt.utils.Pair;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.CMAction;
import com.radixdlt.constraintmachine.TransitionProcedure.ProcedureResult;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
	private final Map<TransitionToken, TransitionProcedure<Particle, UsedData, Particle, UsedData>> scryptTransitionProcedures;
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

	public Map<TransitionToken, TransitionProcedure<Particle, UsedData, Particle, UsedData>> getScryptTransitionProcedures() {
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
	public <O extends Particle> void createTransitionFromRRI(Class<O> particleClass) {
		ParticleDefinition<Particle> particleDefinition = getParticleDefinition(particleClass);
		if (particleDefinition.getRriMapper() == null) {
			throw new IllegalStateException(particleClass + " must be registered with an RRI mapper.");
		}

		createTransitionInternal(
			RRIParticle.class,
			TypeToken.of(VoidUsedData.class),
			particleClass,
			TypeToken.of(VoidUsedData.class),
			(in, inUsed, out, outUsed) -> ProcedureResult.popInputOutput(),
			(res, in, out, meta) -> res == CMAction.POP_INPUT_OUTPUT && meta.isSignedBy(in.getRri().getAddress().getKey())
				? WitnessValidatorResult.success() : WitnessValidatorResult.error("Not signed by " + in.getRri().getAddress())
		);
	}

	@Override
	public <O extends Particle, U extends Particle> void createTransitionFromRRICombined(
		Class<O> particleClass0,
		Class<U> particleClass1,
		BiFunction<O, U, Result> combinedCheck
	) {
		final ParticleDefinition<Particle> particleDefinition0 = getParticleDefinition(particleClass0);
		if (particleDefinition0.getRriMapper() == null) {
			throw new IllegalStateException(particleClass0 + " must be registered with an RRI mapper.");
		}
		final ParticleDefinition<Particle> particleDefinition1 = getParticleDefinition(particleClass1);
		if (particleDefinition1.getRriMapper() == null) {
			throw new IllegalStateException(particleClass1 + " must be registered with an RRI mapper.");
		}

		CombinedTransition<RRIParticle, O, U> combinedTransition = new CombinedTransition<>(
			particleClass0,
			particleClass1,
			combinedCheck
		);
		createTransitionInternal(
			RRIParticle.class,
			TypeToken.of(VoidUsedData.class),
			particleClass0,
			TypeToken.of(VoidUsedData.class),
			combinedTransition.getProcedure0(),
			(res, in, out, meta) -> WitnessValidatorResult.success()
		);
		createTransitionInternal(
			RRIParticle.class,
			TypeToken.of(VoidUsedData.class),
			particleClass1,
			TypeToken.of(VoidUsedData.class),
			combinedTransition.getProcedure1(),
			(res, in, out, meta) -> WitnessValidatorResult.success()
		);
		createTransitionInternal(
			RRIParticle.class,
			new TypeToken<UsedParticle<U>>() { }.where(new TypeParameter<U>() { }, particleClass1),
			particleClass0,
			TypeToken.of(VoidUsedData.class),
			combinedTransition.getProcedure2(),
			(res, in, out, meta) -> {
				if (res == CMAction.POP_INPUT_OUTPUT) {
					if (!meta.isSignedBy(in.getRri().getAddress().getKey())) {
						return WitnessValidatorResult.error("Not signed by " + in.getRri().getAddress());
					}
				}

				return WitnessValidatorResult.success();
			}
		);
		createTransitionInternal(
			RRIParticle.class,
			new TypeToken<UsedParticle<O>>() { }.where(new TypeParameter<O>() { }, particleClass0),
			particleClass1,
			TypeToken.of(VoidUsedData.class),
			combinedTransition.getProcedure3(),
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
	public <I extends Particle, N extends UsedData, O extends Particle, U extends UsedData> void createTransition(
		Class<I> inputClass,
		TypeToken<N> inputUsedType,
		Class<O> outputClass,
		TypeToken<U> outputUsedType,
		TransitionProcedure<I, N, O, U> procedure,
		WitnessValidator<I, O> witnessValidator
	) {
		createTransitionInternal(inputClass, inputUsedType, outputClass, outputUsedType, procedure, witnessValidator);
	}

	private static <I extends Particle, N extends UsedData, O extends Particle, U extends UsedData>
		TransitionProcedure<Particle, UsedData, Particle, UsedData> toGeneric(TransitionProcedure<I, N, O, U> procedure) {
		return (in, inUsed, out, outUsed) -> procedure.execute((I) in, (N) inUsed, (O) out, (U) outUsed);
	}

	private static <I extends Particle, O extends Particle> WitnessValidator<Particle, Particle> toGeneric(WitnessValidator<I, O> validator) {
		return (res, in, out, meta) -> validator.validate(res, (I) in, (O) out, meta);
	}

	private <I extends Particle, N extends UsedData, O extends Particle, U extends UsedData> void createTransitionInternal(
		Class<I> inputClass,
		TypeToken<N> inputUsedClass,
		Class<O> outputClass,
		TypeToken<U> outputUsedClass,
		TransitionProcedure<I, N, O, U> procedure,
		WitnessValidator<I, O> witnessValidator
	) {
		Objects.requireNonNull(inputClass);
		Objects.requireNonNull(outputClass);

		final ParticleDefinition<Particle> inputDefinition = getParticleDefinition(inputClass);
		final ParticleDefinition<Particle> outputDefinition = getParticleDefinition(outputClass);

		final TransitionProcedure<Particle, UsedData, Particle, UsedData> transformedProcedure;

		// RRIs must be the same across RRI particle transitions
		if (inputDefinition.getRriMapper() != null && outputDefinition.getRriMapper() != null) {
			transformedProcedure = (in, inUsed, out, outUsed) -> {
				final RRI inputRRI = inputDefinition.getRriMapper().apply(in);
				final RRI outputRRI = outputDefinition.getRriMapper().apply(out);
				if (!inputRRI.equals(outputRRI)) {
					return ProcedureResult.error("Input/Output RRIs not equal");
				}

				return procedure.execute((I) in, (N) inUsed, (O) out, (U) outUsed);
			};
		} else {
			transformedProcedure = toGeneric(procedure);
		}

		final TransitionToken transitionToken = new TransitionToken(inputClass, inputUsedClass, outputClass, outputUsedClass);
		scryptTransitionProcedures.put(transitionToken, transformedProcedure);
		scryptWitnessValidators.put(Pair.of(inputClass, outputClass), toGeneric(witnessValidator));
	}
}
