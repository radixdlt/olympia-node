package com.radixdlt.atomos.test;

import com.radixdlt.atomos.SysCalls;
import com.radixdlt.atomos.FungibleDefinition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.mapper.ParticleToAmountMapper;
import com.radixdlt.atomos.mapper.ParticleToRRIMapper;
import com.radixdlt.atomos.mapper.ParticleToShardableMapper;
import com.radixdlt.atomos.mapper.ParticleToShardablesMapper;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ConstraintProcedure;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A stubbed AtomOS used for testing Atom Model Application Layer Code.
 * Note that this class is not thread-safe.
 */
public class TestAtomOS implements SysCalls {
	private final Map<Class<? extends Particle>, ParticleToRRIMapper<Particle>> resources = new HashMap<>();
	private final List<Pair<Class<? extends Particle>, BiFunction<Particle, AtomMetadata, Result>>> particleClassConstraints = new ArrayList<>();
	private final List<FungibleDefinition<? extends Particle>> fungibleDefinitions = new ArrayList<>();
	private FungibleDefinition.Builder<? extends Particle> pendingFungibleTransition = null;

	@Override
	public <T extends Particle> void registerParticle(Class<T> particleClass, ParticleToShardablesMapper<T> mapper) {
		// Not implemented for the test AtomOS for the time being as it is not used to test any functionality.
	}

	@Override
	public <T extends Particle> void registerParticle(Class<T> particleClass, ParticleToShardableMapper<T> mapper) {
		// Not implemented for the test AtomOS for the time being as it is not used to test any functionality.
	}

	@Override
	public <T extends Particle> void newRRIResourceType(Class<T> particleClass, ParticleToRRIMapper<T> indexer) {
		resources.put(particleClass, p -> indexer.index((T) p));
	}

	@Override
	public <T extends Particle, U extends Particle> void newRRIResourceType(
		Class<T> particleClass0,
		ParticleToRRIMapper<T> rriMapper0,
		Class<U> particleClass1,
		ParticleToRRIMapper<U> rriMapper1,
		BiPredicate<T, U> combinedResource
	) {
		resources.put(particleClass0, p -> rriMapper0.index((T) p));
	}

	@Override
	public <T extends Particle> ParticleClassConstraint<T> on(Class<T> particleClass) {
		return constraint -> particleClassConstraints.add(new Pair<>(particleClass, (p, m) -> constraint.apply((T) p)));
	}

	@Override
	public void registerProcedure(ConstraintProcedure procedure) {

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
			!resources.containsKey(t.getClass()) || metadata.isSignedBy(resources.get(t.getClass()).index(t).getAddress())
				? Stream.empty() : Stream.of(Result.error("Not signed"));

		Stream<Result> classConstraintResults = particleClassConstraints.stream()
			.filter(p -> p.getFirst().isAssignableFrom(t.getClass()))
			.map(Pair::getSecond)
			.map(constraint -> constraint.apply(t, metadata));

		final List<Result> results = Stream.concat(resourceSigned, classConstraintResults).collect(Collectors.toList());

		return new TestResult(results);
	}
}
