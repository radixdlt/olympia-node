package com.radixdlt.atomos;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.store.CMStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import com.radixdlt.constraintmachine.ConstraintMachine.Builder;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.store.CMStores;
import com.radixdlt.common.EUID;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of the AtomOS interface on top of a UTXO based Constraint Machine.
 */
public final class CMAtomOS {
	private static final ParticleDefinition<Particle> RRI_PARTICLE_DEF = new ParticleDefinition<>(
		rri -> Stream.of(((RRIParticle) rri).getRri().getAddress()),
		rri -> Result.success(),
		rri -> ((RRIParticle) rri).getRri(),
		true
	);

	private final Function<RadixAddress, Result> addressChecker;
	private final Map<Class<? extends Particle>, ParticleDefinition<Particle>> particleDefinitions = new HashMap<>();
	private final ImmutableMap.Builder<Pair<Class<? extends Particle>, Class<? extends Particle>>, TransitionProcedure<Particle, Particle>>
		proceduresBuilder = new ImmutableMap.Builder<>();
	private final ImmutableMap.Builder<Pair<Class<? extends Particle>, Class<? extends Particle>>, WitnessValidator<Particle, Particle>>
		witnessesBuilder = new ImmutableMap.Builder<>();

	public CMAtomOS(Function<RadixAddress, Result> addressChecker) {
		// RRI particle is a low level particle managed by the OS used for the management of all other resources
		this.particleDefinitions.put(RRIParticle.class, RRI_PARTICLE_DEF);
		this.addressChecker = addressChecker;
	}

	public CMAtomOS() {
		this(address -> Result.success());
	}

	public void load(ConstraintScrypt constraintScrypt) {
		ConstraintScryptEnv constraintScryptEnv = new ConstraintScryptEnv(
			ImmutableMap.copyOf(particleDefinitions),
			addressChecker
		);
		constraintScrypt.main(constraintScryptEnv);
		this.particleDefinitions.putAll(constraintScryptEnv.getScryptParticleDefinitions());
		this.proceduresBuilder.putAll(constraintScryptEnv.getScryptTransitionProcedures());
		this.witnessesBuilder.putAll(constraintScryptEnv.getScryptWitnessValidators());
	}

	/**
	 * Checks that the machine is set up correctly where invariants aren't broken.
	 * If all is well, this then returns an instance of a machine in which atom
	 * validation can be done with the Particles and Transitions it's been set up with.
	 *
	 * @return a constraint machine which can validate atoms and the virtual layer on top of the store
	 */
	public ConstraintMachine buildMachine() {
		ConstraintMachine.Builder cmBuilder = new Builder();

		final ImmutableMap<Pair<Class<? extends Particle>, Class<? extends Particle>>, TransitionProcedure<Particle, Particle>>
			procedures = proceduresBuilder.build();
		cmBuilder.setParticleProcedures((input, output) -> procedures.get(
			Pair.<Class<? extends Particle>, Class<? extends Particle>>of(
				input == null ? null : input.getClass(),
				output == null ? null : output.getClass())
		));
		final ImmutableMap<Pair<Class<? extends Particle>, Class<? extends Particle>>, WitnessValidator<Particle, Particle>>
			witnessValidators = witnessesBuilder.build();
		cmBuilder.setWitnessValidators((in, out) -> witnessValidators.get(
			Pair.<Class<? extends Particle>, Class<? extends Particle>>of(
				in == null ? null : in.getClass(),
				out == null ? null : out.getClass())
		));

		cmBuilder.setParticleStaticCheck(p -> {
			final ParticleDefinition<Particle> particleDefinition = particleDefinitions.get(p.getClass());
			if (particleDefinition == null) {
				return Result.error("Unknown particle type: " + p.getClass());
			}

			final Function<Particle, Result> staticValidation = particleDefinition.getStaticValidation();
			final Result staticCheckResult = staticValidation.apply(p);
			if (staticCheckResult.isError()) {
				return staticCheckResult;
			}

			final Function<Particle, Stream<RadixAddress>> mapper = particleDefinition.getAddressMapper();
			final Set<EUID> destinations = mapper.apply(p).map(RadixAddress::getUID).collect(Collectors.toSet());

			if (!destinations.containsAll(p.getDestinations())) {
				return Result.error("Address destinations does not contain all destinations");
			}

			if (!p.getDestinations().containsAll(destinations)) {
				return Result.error("Destinations does not contain all Address destinations");
			}

			return Result.success();
		});

		UnaryOperator<CMStore> rriTransformer = base ->
			CMStores.virtualizeDefault(base, p -> p instanceof RRIParticle && ((RRIParticle) p).getNonce() == 0, Spin.UP);

		UnaryOperator<CMStore> virtualizedDefault = base -> {
			CMStore virtualizeNeutral = CMStores.virtualizeDefault(base, p -> {
				final ParticleDefinition<Particle> particleDefinition = particleDefinitions.get(p.getClass());
				if (particleDefinition == null) {
					return false;
				}

				final Function<Particle, Result> staticValidation = particleDefinition.getStaticValidation();
				if (staticValidation.apply(p).isError()) {
					return false;
				}

				final Function<Particle, Stream<RadixAddress>> mapper = particleDefinition.getAddressMapper();
				final Set<EUID> destinations = mapper.apply(p).map(RadixAddress::getUID).collect(Collectors.toSet());

				return !(destinations.isEmpty())
					&& destinations.containsAll(p.getDestinations())
					&& p.getDestinations().containsAll(destinations);
			}, Spin.NEUTRAL);

			return rriTransformer.apply(virtualizeNeutral);
		};

		cmBuilder.virtualStore(virtualizedDefault);

		return cmBuilder.build();
	}
}
