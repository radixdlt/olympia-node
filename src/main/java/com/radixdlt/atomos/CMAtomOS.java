package com.radixdlt.atomos;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.AtomOSKernel.AtomKernelCompute;
import com.radixdlt.common.Pair;
import com.radixdlt.compute.AtomCompute;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionProcedure.ProcedureResult;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.store.CMStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import com.radixdlt.constraintmachine.ConstraintMachine.Builder;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.KernelConstraintProcedure;
import com.radixdlt.constraintmachine.KernelProcedureError;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.store.CMStores;
import com.radixdlt.common.EUID;
import com.radixdlt.universe.Universe;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of the AtomOS interface on top of a UTXO based Constraint Machine.
 */
public final class CMAtomOS {
	private final List<KernelConstraintProcedure> kernelProcedures = new ArrayList<>();
	private AtomKernelCompute atomKernelCompute;

	private final Map<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions = new LinkedHashMap<>();
	private final ImmutableMap.Builder<Pair<Class<? extends Particle>, Class<? extends Particle>>, TransitionProcedure<Particle, Particle>> proceduresBuilder
		= new ImmutableMap.Builder<>();
	private final ImmutableMap.Builder<Pair<Class<? extends Particle>, Class<? extends Particle>>, WitnessValidator<Particle, Particle>> witnessesBuilder
		= new ImmutableMap.Builder<>();

	private final Supplier<Universe> universeSupplier;
	private final LongSupplier timestampSupplier;

	public CMAtomOS(
		Supplier<Universe> universeSupplier,
		LongSupplier timestampSupplier
	) {
		this.universeSupplier = Objects.requireNonNull(universeSupplier);
		this.timestampSupplier = Objects.requireNonNull(timestampSupplier);

		// RRI particle is a low level particle managed by the OS used for the management of all other resources
		this.particleDefinitions.put(RRIParticle.class, new ParticleDefinition<>(
			rri -> Stream.of(((RRIParticle) rri).getRri().getAddress()),
			rri -> Result.success()
		));
	}

	private static <T extends Particle, U extends Particle> TransitionProcedure<Particle, Particle> toGeneric(TransitionProcedure<T, U> procedure) {
		return (in, inData, out, outData) -> procedure.execute((T) in, inData, (U) out, outData);
	}

	private static <T extends Particle, U extends Particle> WitnessValidator<Particle, Particle> toGeneric(WitnessValidator<T, U> validator) {
		return (res, in, out, meta) -> validator.validate(res, (T) in, (U) out, meta);
	}

	private static class ParticleDefinition<T extends Particle> {
		private final Function<T, Stream<RadixAddress>> addressMapper;
		private final Function<T, Result> staticValidation;

		ParticleDefinition(Function<T, Stream<RadixAddress>> addressMapper, Function<T, Result> staticValidation) {
			this.staticValidation = staticValidation;
			this.addressMapper = addressMapper;
		}
	}

	public void load(ConstraintScrypt constraintScrypt) {
		final Map<Class<? extends Particle>, ParticleDefinition<Particle>> scryptParticleDefinitions = new HashMap<>();

		constraintScrypt.main(new SysCalls() {
			@Override
			public <T extends Particle> void registerParticle(
				Class<T> particleClass,
				Function<T, RadixAddress> mapper,
				Function<T, Result> staticCheck
			) {
				registerParticleMultipleAddress(
					particleClass,
					(T particle) -> Collections.singleton(mapper.apply(particle)),
					staticCheck
				);
			}

			@Override
			public <T extends Particle> void registerParticleMultipleAddress(
				Class<T> particleClass,
				Function<T, Set<RadixAddress>> mapper,
				Function<T, Result> staticCheck
			) {
				if (particleDefinitions.containsKey(particleClass)) {
					throw new IllegalStateException("Particle " + particleClass + " is already registered");
				}

				scryptParticleDefinitions.put(particleClass, new ParticleDefinition<>(
					p -> mapper.apply((T) p).stream(),
					p -> staticCheck.apply((T) p)
				));
			}

			@Override
			public <T extends Particle> void createRRIType(
				Class<T> particleClass,
				Function<T, RRI> rriMapper
			) {
				if (!scryptParticleDefinitions.containsKey(particleClass)) {
					throw new IllegalStateException(particleClass + " must be registered in calling scrypt.");
				}

				final TransitionProcedure<RRIParticle, T> procedure = new RRIResourceCreation<>(rriMapper);
				createTransitionInternal(
					RRIParticle.class,
					particleClass,
					procedure,
					(res, in, out, meta) -> res == ProcedureResult.POP_INPUT_OUTPUT && meta.isSignedBy(in.getRri().getAddress())
				);
			}

			@Override
			public <T extends Particle, U extends Particle> void createCombinedRRIType(
				Class<T> particleClass0,
				Function<T, RRI> rriMapper0,
				Class<U> particleClass1,
				Function<U, RRI> rriMapper1,
				BiPredicate<T, U> combinedCheck
			) {
				if (!scryptParticleDefinitions.containsKey(particleClass0)) {
					throw new IllegalStateException(particleClass0 + " must be registered in calling scrypt.");
				}
				if (!scryptParticleDefinitions.containsKey(particleClass1)) {
					throw new IllegalStateException(particleClass1 + " must be registered in calling scrypt.");
				}

				final TransitionProcedure<RRIParticle, T> procedure0 = new RRIResourceCombinedPrimaryCreation<>(rriMapper0);
				createTransitionInternal(
					RRIParticle.class,
					particleClass0,
					procedure0,
					(res, in, out, meta) -> res == ProcedureResult.POP_OUTPUT
				);

				final TransitionProcedure<RRIParticle, U> procedure1 = new RRIResourceCombinedDependentCreation<>(
					particleClass0,
					rriMapper1,
					combinedCheck
				);
				createTransitionInternal(
					RRIParticle.class,
					particleClass1,
					procedure1,
					(res, in, out, meta) -> res == ProcedureResult.POP_INPUT_OUTPUT && meta.isSignedBy(in.getRri().getAddress())
				);
			}

			@Override
			public <T extends Particle, U extends Particle> void createTransition(
				Class<T> inputClass,
				Class<U> outputClass,
				TransitionProcedure<T, U> procedure,
				WitnessValidator<T, U> witnessValidator
			) {
				if ((inputClass != null && !scryptParticleDefinitions.containsKey(inputClass))
					|| (outputClass != null && !scryptParticleDefinitions.containsKey(outputClass))) {
					throw new IllegalStateException(inputClass + " " + outputClass + " must be all registered in calling scrypt.");
				}
				createTransitionInternal(inputClass, outputClass, procedure, witnessValidator);
			}

			private <T extends Particle, U extends Particle> void createTransitionInternal(
				Class<T> inputClass,
				Class<U> outputClass,
				TransitionProcedure<T, U> procedure,
				WitnessValidator<T, U> witnessValidator
			) {
				proceduresBuilder.put(Pair.of(inputClass, outputClass), toGeneric(procedure));
				witnessesBuilder.put(Pair.of(inputClass, outputClass), toGeneric(witnessValidator));
			}
		});

		particleDefinitions.putAll(scryptParticleDefinitions);
	}

	public void loadKernelConstraintScrypt(AtomOSDriver driverScrypt) {
		driverScrypt.main(new AtomOSKernel() {
			@Override
			public AtomKernel onAtom() {
				return new AtomKernel() {
					@Override
					public void require(AtomKernelConstraintCheck constraint) {
						CMAtomOS.this.kernelProcedures.add(
							(cmAtom) -> constraint.check(cmAtom).errorStream().map(errMsg -> KernelProcedureError.of(cmAtom.getAtom(), errMsg))
						);
					}

					@Override
					public void setCompute(AtomKernelCompute compute) {

						if (CMAtomOS.this.atomKernelCompute != null) {
							throw new IllegalStateException("Compute already set.");
						}

						CMAtomOS.this.atomKernelCompute = compute;
					}
				};
			}

			@Override
			public long getCurrentTimestamp() {
				return timestampSupplier.getAsLong();
			}

			@Override
			public Universe getUniverse() {
				return universeSupplier.get();
			}
		});
	}

	/**
	 * Checks that the machine is set up correctly where invariants aren't broken.
	 * If all is well, this then returns an instance of a machine in which atom
	 * validation can be done with the Quarks and Particles it's been set up with.
	 *
	 * @return a constraint machine which can validate atoms and the virtual layer on top of the store
	 */
	public Pair<ConstraintMachine, AtomCompute> buildMachine() {
		ConstraintMachine.Builder cmBuilder = new Builder();

		this.kernelProcedures.forEach(cmBuilder::addProcedure);

		ImmutableMap<Pair<Class<? extends Particle>, Class<? extends Particle>>, TransitionProcedure<Particle, Particle>> procedures = proceduresBuilder.build();
		cmBuilder.setParticleProcedures((input, output) -> procedures.get(
			Pair.<Class<? extends Particle>, Class<? extends Particle>>of(
				input == null ? null : input.getClass(),
				output == null ? null : output.getClass())
		));
		ImmutableMap<Pair<Class<? extends Particle>, Class<? extends Particle>>, WitnessValidator<Particle, Particle>> witnessValidators = witnessesBuilder.build();
		cmBuilder.setWitnessValidators((in, out) -> witnessValidators.get(
			Pair.<Class<? extends Particle>, Class<? extends Particle>>of(
				in == null ? null : in.getClass(),
				out == null ? null : out.getClass())
		));

		UnaryOperator<CMStore> rriTransformer = base ->
			CMStores.virtualizeDefault(base, p -> p instanceof RRIParticle && ((RRIParticle) p).getNonce() == 0, Spin.UP);

		UnaryOperator<CMStore> virtualizedDefault = base -> {
			CMStore virtualizeNeutral = CMStores.virtualizeDefault(base, p -> {
				final ParticleDefinition<Particle> particleDefinition = particleDefinitions.get(p.getClass());
				if (particleDefinition == null) {
					return false;
				}

				final Function<Particle, Result> staticValidation = particleDefinition.staticValidation;
				if (staticValidation.apply(p).isError()) {
					return false;
				}

				final Function<Particle, Stream<RadixAddress>> mapper = particleDefinition.addressMapper;
				final Set<EUID> destinations = mapper.apply(p).map(RadixAddress::getUID).collect(Collectors.toSet());

				return !(destinations.isEmpty())
					&& destinations.containsAll(p.getDestinations())
					&& p.getDestinations().containsAll(destinations);
			}, Spin.NEUTRAL);

			return rriTransformer.apply(virtualizeNeutral);
		};

		cmBuilder.virtualStore(virtualizedDefault);

		final AtomCompute compute = atomKernelCompute != null ? a -> atomKernelCompute.compute(a.getAtom()) : null;

		return Pair.of(cmBuilder.build(), compute);
	}
}
