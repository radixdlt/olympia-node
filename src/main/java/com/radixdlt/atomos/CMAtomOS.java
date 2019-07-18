package com.radixdlt.atomos;

import com.radixdlt.common.Pair;
import com.radixdlt.compute.AtomCompute;
import com.radixdlt.store.CMStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import com.radixdlt.atomos.mapper.ParticleToAmountMapper;
import com.radixdlt.atomos.mapper.ParticleToRRIMapper;
import com.radixdlt.atomos.mapper.ParticleToShardableMapper;
import com.radixdlt.atomos.mapper.ParticleToShardablesMapper;
import com.radixdlt.atomos.procedures.ParticleClassConstraintProcedure;
import com.radixdlt.atomos.procedures.ParticleClassWithSideEffectConstraintProcedure;
import com.radixdlt.atomos.procedures.PayloadParticleConstraintProcedure;
import com.radixdlt.atomos.procedures.RRIConstraintProcedure;
import com.radixdlt.atomos.procedures.fungible.FungibleTransitionConstraintProcedure;
import com.radixdlt.constraintmachine.ConstraintMachine.Builder;
import com.radixdlt.constraintmachine.ConstraintProcedure;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.KernelConstraintProcedure;
import com.radixdlt.constraintmachine.KernelProcedureError;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.store.CMStores;
import com.radixdlt.common.EUID;
import com.radixdlt.universe.Universe;

import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of the AtomOS interface on top of a UTXO based Constraint Machine.
 */
public final class CMAtomOS implements AtomOSKernel, AtomOS {
	private static final Pattern PARTICLE_NAME_PATTERN = Pattern.compile("[1-9A-Za-z]+");
	private final List<ConstraintProcedure> procedures = new ArrayList<>();
	private final List<KernelConstraintProcedure> kernelProcedures = new ArrayList<>();
	private AtomKernelCompute atomKernelCompute;

	private final List<FungibleTransition<? extends Particle>> fungibleTransitions = new ArrayList<>();
	private FungibleTransition.Builder<? extends Particle> pendingFungibleTransition = null;
	private final Map<Class<? extends Particle>, Function<Particle, Stream<RadixAddress>>> particleMapper = new LinkedHashMap<>();

	private final RRIConstraintProcedure.Builder rriProcedureBuilder = new RRIConstraintProcedure.Builder();
	private final PayloadParticleConstraintProcedure.Builder payloadProcedureBuilder = new PayloadParticleConstraintProcedure.Builder();
	private final Supplier<Universe> universeSupplier;
	private final LongSupplier timestampSupplier;

	public CMAtomOS(
		Supplier<Universe> universeSupplier,
		LongSupplier timestampSupplier
	) {
		this.universeSupplier = Objects.requireNonNull(universeSupplier);
		this.timestampSupplier = Objects.requireNonNull(timestampSupplier);

		// RRI particle is a low level particle managed by the OS used for the management of all other resources
		this.registerParticle(RRIParticle.class, "rri", (RRIParticle rri) -> rri.getRri().getAddress());
	}

	public void load(ConstraintScrypt constraintScrypt) {
		constraintScrypt.main(this);

		if (this.pendingFungibleTransition != null) {
			this.fungibleTransitions.add(this.pendingFungibleTransition.build());
			this.pendingFungibleTransition = null;
		}
	}

	public void loadKernelConstraintScrypt(AtomOSDriver driverScrypt) {
		driverScrypt.main(this);
	}


	@Override
	public <T extends Particle> void registerParticle(Class<T> particleClass, String name, ParticleToShardablesMapper<T> mapper) {
		if (particleMapper.containsKey(particleClass)) {
			throw new IllegalStateException("Particle " + particleClass + " is already registered");
		}

		if (!PARTICLE_NAME_PATTERN.matcher(name).matches()) {
			throw new IllegalArgumentException("Particle identifier " + name + " must follow regex "
				+ PARTICLE_NAME_PATTERN.toString());
		}

		particleMapper.put(particleClass, p -> mapper.getDestinations((T) p).stream());
	}

	@Override
	public <T extends Particle> void registerParticle(Class<T> particleClass, String name, ParticleToShardableMapper<T> mapper) {
		registerParticle(particleClass, name, (T particle) -> Collections.singleton(mapper.getDestination(particle)));
	}

	@Override
	public <T extends Particle> PayloadParticleClassConstraint<T> onPayload(Class<T> particleClass) {
		checkParticleRegistered(particleClass);

		payloadProcedureBuilder.add(particleClass);

		return constraintCheck -> {
			ParticleClassConstraintProcedure<T> procedure = new ParticleClassConstraintProcedure<>(particleClass, constraintCheck);
			procedures.add(procedure);
		};
	}

	@Override
	public <T extends Particle> IndexedConstraint<T> onIndexed(Class<T> particleClass, ParticleToRRIMapper<T> rriMapper) {
		checkParticleRegistered(particleClass);

		return invariant -> {
			rriProcedureBuilder.add(particleClass, rriMapper);

			return new InitializedIndexedConstraint<T>() {
				@Override
				public <U extends Particle> void requireInitialWith(Class<U> sideEffectClass,
					ParticleClassWithSideEffectConstraintCheck<T, U> constraint) {

					ParticleClassWithSideEffectConstraintProcedure<T, U> procedure
						= new ParticleClassWithSideEffectConstraintProcedure<>(particleClass, sideEffectClass, constraint);
					procedures.add(procedure);
				}
			};
		};
	}

	private <T extends Particle> void checkParticleRegistered(Class<T> particleClass) {
		if (!particleMapper.containsKey(particleClass)) {
			throw new IllegalStateException(particleClass + " is not registered.");
		}
	}

	@Override
	public <T extends Particle> ParticleClassConstraint<T> on(Class<T> particleClass) {
		checkParticleRegistered(particleClass);

		return constraint -> {
			ParticleClassConstraintProcedure<T> procedure = new ParticleClassConstraintProcedure<>(particleClass, (p, m) -> constraint.apply(p));
			procedures.add(procedure);
		};
	}

	@Override
	public <T extends Particle> FungibleTransitionConstraintStub<T> onFungible(
		Class<T> particleClass,
		ParticleToAmountMapper<T> particleToAmountMapper,
		BiFunction<T, T, Boolean> fungibleEquals
	) {
		checkParticleRegistered(particleClass);

		if (pendingFungibleTransition != null) {
			fungibleTransitions.add(pendingFungibleTransition.build());
		}

		FungibleTransition.Builder<T> transitionBuilder = FungibleTransition.<T>build()
			.to(particleClass, particleToAmountMapper, fungibleEquals);
		pendingFungibleTransition = transitionBuilder;

		return new FungibleTransitionConstraintStub<T>() {
			@Override
			public <U extends Particle> FungibleTransitionConstraint<T> requireInitialWith(
				Class<U> sideEffectClass,
				ParticleClassWithSideEffectConstraintCheck<T, U> constraint
			) {
				transitionBuilder.initialWith(sideEffectClass, constraint);
				return this::requireFrom;
			}

			@Override
			public <U extends Particle> FungibleTransitionConstraint<T> requireFrom(
				Class<U> cls1,
				FungibleTransitionInputConstraint<U, T> check
			) {
				if (pendingFungibleTransition == null) {
					throw new IllegalStateException("Attempt to add formula to finished fungible transition to " + particleClass);
				}
				FungibleFormula formula = FungibleFormula.from(new FungibleTransitionMember<>(cls1, check));
				transitionBuilder.addFormula(formula);
				return this::requireFrom;
			}
		};
	}

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
	public Universe getUniverse() {
		return universeSupplier.get();
	}

	@Override
	public long getCurrentTimestamp() {
		return timestampSupplier.getAsLong();
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

		this.procedures.forEach(cmBuilder::addProcedure);
		this.kernelProcedures.forEach(cmBuilder::addProcedure);

		// Add a constraint for fungibles if any were added
		if (!this.fungibleTransitions.isEmpty()) {
			cmBuilder.addProcedure(new FungibleTransitionConstraintProcedure(this.fungibleTransitions));
		}

		// Add constraint for RRI state machines
		cmBuilder.addProcedure(this.rriProcedureBuilder.build());
		// Add constraint for Payload state machines
		cmBuilder.addProcedure(this.payloadProcedureBuilder.build());

		UnaryOperator<CMStore> rriTransformer = base ->
			CMStores.virtualizeDefault(base, p -> p instanceof RRIParticle && ((RRIParticle) p).getNonce() == 0, Spin.UP);

		UnaryOperator<CMStore> virtualizedDefault = base -> {
			CMStore virtualizeNeutral = CMStores.virtualizeDefault(base, p -> {
				Function<Particle, Stream<RadixAddress>> mapper = particleMapper.get(p.getClass());
				if (mapper == null) {
					return false;
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
