package com.radixdlt.atomos.test;

import com.radixdlt.atomos.RRI;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.Result;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.WitnessValidator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A stubbed AtomOS used for testing Atom Model Application Layer Code.
 * Note that this class is not thread-safe.
 */
public class TestAtomOS implements SysCalls {
	private final Map<Class<? extends Particle>, Function<Particle, RRI>> resources = new HashMap<>();
	private final List<Pair<Class<? extends Particle>, BiFunction<Particle, AtomMetadata, Result>>> particleClassConstraints = new ArrayList<>();

	@Override
	public <T extends Particle> void registerParticleMultipleAddresses(
		Class<T> particleClass,
		Function<T, Set<RadixAddress>> mapper,
		Function<T, Result> staticCheck,
		Function<T, RRI> rriMapper
	) {
		resources.put(particleClass, p -> rriMapper.apply((T) p));
	}

	@Override
	public <T extends Particle> void registerParticleMultipleAddresses(
		Class<T> particleClass,
		Function<T, Set<RadixAddress>> mapper,
		Function<T, Result> staticCheck
	) {
		// Not implemented for the test AtomOS for the time being as it is not used to test any functionality.
	}

	@Override
	public <T extends Particle> void registerParticle(
		Class<T> particleClass,
		Function<T, RadixAddress> mapper,
		Function<T, Result> staticCheck
	) {
		particleClassConstraints.add(new Pair<>(particleClass, (p, m) -> staticCheck.apply((T) p)));
	}

	@Override
	public <T extends Particle> void registerParticle(
		Class<T> particleClass,
		Function<T, RadixAddress> mapper,
		Function<T, Result> staticCheck,
		Function<T, RRI> rriMapper
	) {
		particleClassConstraints.add(new Pair<>(particleClass, (p, m) -> staticCheck.apply((T) p)));
	}

	@Override
	public <T extends Particle> void createTransitionFromRRI(Class<T> particleClass) {
	}

	@Override
	public <T extends Particle, U extends Particle> void createTransitionFromRRICombined(
		Class<T> particleClass0,
		Class<U> particleClass1,
		BiFunction<T, U, Result> combinedCheck
	) {
	}

	@Override
	public <T extends Particle, U extends Particle> void createTransition(
		Class<T> inputClass,
		Class<U> outputClass,
		TransitionProcedure<T, U> procedure,
		WitnessValidator<T, U> witnessValidator
	) {
	}

	/**
	 * Mimics a constraint check call to particle class checkers
	 *
	 * @param <T> class of particle
	 * @param t particle to be sent to the check call
	 * @param metadata
	 * @return list of results of each checker
	 */
	public <T extends Particle> TestResult testInitialParticle(T t, AtomMetadata metadata) {
		Stream<Result> resourceSigned =
			!resources.containsKey(t.getClass()) || metadata.isSignedBy(resources.get(t.getClass()).apply(t).getAddress())
				? Stream.empty() : Stream.of(Result.error("Not signed"));

		Stream<Result> classConstraintResults = particleClassConstraints.stream()
			.filter(p -> p.getFirst().isAssignableFrom(t.getClass()))
			.map(Pair::getSecond)
			.map(constraint -> constraint.apply(t, metadata));

		final List<Result> results = Stream.concat(resourceSigned, classConstraintResults).collect(Collectors.toList());

		return new TestResult(results);
	}
}
