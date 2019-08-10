package com.radixdlt.atomos;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.AtomOSKernel.AtomKernelCompute;
import com.radixdlt.common.Pair;
import com.radixdlt.compute.AtomCompute;
import com.radixdlt.constraintmachine.ParticleProcedure;
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
import com.radixdlt.atomos.mapper.ParticleToAmountMapper;
import com.radixdlt.atomos.mapper.ParticleToRRIMapper;
import com.radixdlt.atomos.mapper.ParticleToShardableMapper;
import com.radixdlt.atomos.mapper.ParticleToShardablesMapper;
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

	private final Map<Class<? extends Particle>, Function<Particle, Stream<RadixAddress>>> particleMapper = new LinkedHashMap<>();
	private final Map<Class<? extends Particle>, Function<Particle, Result>> particleStaticValidation = new HashMap<>();

	private final Map<Class<? extends Particle>, FungibleDefinition.Builder<? extends Particle>> fungibles = new HashMap<>();
	private final RRIParticleProcedureBuilder rriProcedureBuilder = new RRIParticleProcedureBuilder();
	private final TransitionlessParticlesProcedureBuilder payloadProcedureBuilder = new TransitionlessParticlesProcedureBuilder();

	private final Supplier<Universe> universeSupplier;
	private final LongSupplier timestampSupplier;

	public CMAtomOS(
		Supplier<Universe> universeSupplier,
		LongSupplier timestampSupplier
	) {
		this.universeSupplier = Objects.requireNonNull(universeSupplier);
		this.timestampSupplier = Objects.requireNonNull(timestampSupplier);

		// RRI particle is a low level particle managed by the OS used for the management of all other resources
		this.particleMapper.put(RRIParticle.class, rri -> Stream.of(((RRIParticle) rri).getRri().getAddress()));
	}

	public void load(ConstraintScrypt constraintScrypt) {
		final Map<Class<? extends Particle>, Function<Particle, Stream<RadixAddress>>> scryptParticleClasses = new HashMap<>();

		constraintScrypt.main(new SysCalls() {
			@Override
			public <T extends Particle> void registerParticle(Class<T> particleClass, ParticleToShardablesMapper<T> mapper) {
				if (scryptParticleClasses.containsKey(particleClass) || particleMapper.containsKey(particleClass)) {
					throw new IllegalStateException("Particle " + particleClass + " is already registered");
				}

				scryptParticleClasses.put(particleClass, p -> mapper.getDestinations((T) p).stream());
			}

			@Override
			public <T extends Particle> void registerParticle(Class<T> particleClass, ParticleToShardableMapper<T> mapper) {
				registerParticle(particleClass, (T particle) -> Collections.singleton(mapper.getDestination(particle)));
			}

			@Override
			public <T extends Particle> ParticleClassConstraint<T> on(Class<T> particleClass) {
				if (!scryptParticleClasses.containsKey(particleClass)) {
					throw new IllegalStateException(particleClass + " must be registered in calling scrypt.");
				}

				return constraint -> {
					particleStaticValidation.merge(particleClass, p -> constraint.apply((T) p),
						(old, next) -> p -> Result.combine(old.apply(p), next.apply(p)));
				};
			}

			@Override
			public <T extends Particle> void newRRIResourceType(
				Class<T> particleClass,
				ParticleToRRIMapper<T> rriMapper
			) {
				if (!scryptParticleClasses.containsKey(particleClass)) {
					throw new IllegalStateException(particleClass + " must be registered in calling scrypt.");
				}

				rriProcedureBuilder.add(particleClass, rriMapper);
			}

			@Override
			public <T extends Particle, U extends Particle> void newRRIResourceType(
				Class<T> particleClass0,
				ParticleToRRIMapper<T> rriMapper0,
				Class<U> particleClass1,
				ParticleToRRIMapper<U> rriMapper1,
				BiPredicate<T, U> combinedResource
			) {
				if (!scryptParticleClasses.containsKey(particleClass0)) {
					throw new IllegalStateException(particleClass0 + " must be registered in calling scrypt.");
				}
				if (!scryptParticleClasses.containsKey(particleClass1)) {
					throw new IllegalStateException(particleClass1 + " must be registered in calling scrypt.");
				}

				rriProcedureBuilder.add(particleClass0, rriMapper0, particleClass1, rriMapper1, combinedResource);
			}

			@Override
			public <T extends Particle> FungibleTransitionConstraint<T> onFungible(
				Class<T> particleClass,
				ParticleToAmountMapper<T> particleToAmountMapper
			) {
				if (!scryptParticleClasses.containsKey(particleClass)) {
					throw new IllegalStateException(particleClass + " must be registered in calling scrypt.");
				}

				if (fungibles.containsKey(particleClass)) {
					throw new IllegalStateException(particleClass + " already registered as fungible.");
				}

				FungibleDefinition.Builder<T> fungibleBuilder = new FungibleDefinition.Builder<T>().amountMapper(particleToAmountMapper);
				fungibles.put(particleClass, fungibleBuilder);

				return new FungibleTransitionConstraint<T>() {
					@Override
					public <U extends Particle> FungibleTransitionConstraint<T> transitionTo(
						Class<U> toParticleClass,
						BiPredicate<T, U> transition,
						WitnessValidator<T> witnessValidator
					) {
						if (!scryptParticleClasses.containsKey(toParticleClass)) {
							throw new IllegalStateException(toParticleClass + " must be registered in calling scrypt.");
						}
						fungibleBuilder.to(toParticleClass, witnessValidator, transition);
						return this::transitionTo;
					}
				};
			}

			@Override
			public <T extends Particle> void newResourceType(
				Class<T> particleClass,
				WitnessValidator<T> witnessValidator
			) {
				if (!scryptParticleClasses.containsKey(particleClass)) {
					throw new IllegalStateException(particleClass + " must be registered in calling scrypt.");
				}

				payloadProcedureBuilder.add(particleClass, witnessValidator);
			}
		});

		particleMapper.putAll(scryptParticleClasses);
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

		ImmutableMap.Builder<Class<? extends Particle>, ParticleProcedure> particleProceduresBuilder = new ImmutableMap.Builder<>();
		// Add a constraint for fungibles if any were added
		if (!this.fungibles.isEmpty()) {
			FungibleParticlesProcedureBuilder fungibleBuilder = new FungibleParticlesProcedureBuilder();
			this.fungibles.forEach((c, b) -> fungibleBuilder.add(c, b.build()));
			fungibleBuilder.build().forEach(particleProceduresBuilder::put);
		}

		// Add constraint for RRI state machines
		particleProceduresBuilder.put(RRIParticle.class, this.rriProcedureBuilder.build());

		// Add constraint for Transitionless state machines
		this.payloadProcedureBuilder.build().forEach(particleProceduresBuilder::put);

		final ImmutableMap<Class<? extends Particle>, ParticleProcedure> particleProcedures = particleProceduresBuilder.build();
		cmBuilder.setParticleProcedures(p -> particleProcedures.get(p.getClass()));

		UnaryOperator<CMStore> rriTransformer = base ->
			CMStores.virtualizeDefault(base, p -> p instanceof RRIParticle && ((RRIParticle) p).getNonce() == 0, Spin.UP);

		UnaryOperator<CMStore> virtualizedDefault = base -> {
			CMStore virtualizeNeutral = CMStores.virtualizeDefault(base, p -> {
				Function<Particle, Stream<RadixAddress>> mapper = particleMapper.get(p.getClass());
				if (mapper == null) {
					return false;
				}

				Function<Particle, Result> staticValidation = particleStaticValidation.get(p.getClass());
				if (staticValidation != null) {
					if (staticValidation.apply(p).isError()) {
						return false;
					}
				}

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
