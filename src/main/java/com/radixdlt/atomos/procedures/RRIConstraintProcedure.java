package com.radixdlt.atomos.procedures;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Streams;
import com.radixdlt.atomos.RRI;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atomos.mapper.ParticleToRRIMapper;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.Spin;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ConstraintProcedure;
import com.radixdlt.constraintmachine.ProcedureError;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Low level code for Indexed Constraints which manages new atoms,
 * state and when to re-check that constraints have been maintained
 * given new atoms
 */
public final class RRIConstraintProcedure implements ConstraintProcedure {
	private final Map<Class<? extends Particle>, ParticleToRRIMapper<Particle>> indexedParticles;

	RRIConstraintProcedure(Map<Class<? extends Particle>, ParticleToRRIMapper<Particle>> indexedParticles) {
		this.indexedParticles = ImmutableMap.copyOf(indexedParticles);
	}

	public static final class Builder {
		private final Map<Class<? extends Particle>, ParticleToRRIMapper<Particle>> indexedParticles;

		public Builder() {
			indexedParticles = new HashMap<>();
		}

		public <T extends Particle> Builder add(Class<T> particleClass, ParticleToRRIMapper<T> indexedParticle) {
			this.indexedParticles.put(particleClass, p -> indexedParticle.index((T)p));
			return this;
		}

		public RRIConstraintProcedure build() {
			return new RRIConstraintProcedure(indexedParticles);
		}
	}

	private Stream<ProcedureError> checkSignedByAddress(ParticleGroup group, AtomMetadata metadata) {
		return group.particlesWithIndex(RRIParticle.class, Spin.DOWN, (rriParticle, i) ->
			!metadata.isSignedBy(rriParticle.getRri().getAddress())
				? Stream.of(ProcedureError.of(group, "rri must be signed by address to use", i))
				: Stream.<ProcedureError>empty()
		).flatMap(l -> l);
	}

	private Stream<ProcedureError> checkUpRRIs(ParticleGroup group) {
		return group.particlesWithIndex(RRIParticle.class, Spin.UP, (rriParticle, i) ->
			ProcedureError.of(group, "rris cannot currently be created via atom ", i)
		);
	}

	private Stream<ProcedureError> checkDownedParticles(ParticleGroup group) {
		return indexedParticles.keySet().stream()
			.flatMap(particleClass ->
				group.particlesWithIndex(particleClass, Spin.DOWN, (particle, i) ->
					ProcedureError.of(group,"rri indexed particles currently can't be downed: " + particle, i))
			);
	}

	private static String toBadMatchString(Iterable<RRI> unspentInputs, Iterable<RRI> unspentOutputs) {
		return "unconsumed inputs: " + unspentInputs + " unspent outputs: " + unspentOutputs;
	}

	private Stream<ProcedureError> checkMatch(ParticleGroup group) {
		List<RRI> rrisConsumed = group.particles(Spin.UP)
			.filter(p -> indexedParticles.containsKey(p.getClass()))
			.map(p -> indexedParticles.get(p.getClass()).index(p))
			.collect(Collectors.toList());

		Multiset<RRI> rrisAvailable = HashMultiset.create();
		group.particles(Spin.DOWN)
			.filter(RRIParticle.class::isInstance)
			.map(RRIParticle.class::cast)
			.map(RRIParticle::getRri)
			.forEach(rrisAvailable::add);

		final List<RRI> unspentOutputs = new ArrayList<>();

		for (RRI rri : rrisConsumed) {
			if (!rrisAvailable.remove(rri)) {
				unspentOutputs.add(rri);
			}
		}

		if (!unspentOutputs.isEmpty() || !rrisAvailable.isEmpty()) {
			return Stream.of(ProcedureError.of(toBadMatchString(rrisAvailable, unspentOutputs)));
		}

		return Stream.empty();
	}

	@Override
	public Stream<ProcedureError> validate(ParticleGroup group, AtomMetadata metadata) {
		return Streams.concat(
			checkSignedByAddress(group, metadata),
			checkUpRRIs(group),
			checkDownedParticles(group),
			checkMatch(group)
		);
	}
}
