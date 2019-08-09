package com.radixdlt.atomos;

import com.radixdlt.atomos.mapper.ParticleToRRIMapper;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ParticleProcedure;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.function.BiPredicate;

/**
 * Low level code for Indexed Constraints which manages new atoms,
 * state and when to re-check that constraints have been maintained
 * given new atoms
 */
public final class RRIParticleProcedureBuilder {
	private static class SecondaryResource<T extends Particle> {
		private final Class<T> particleClass;
		private final ParticleToRRIMapper<Particle> rriMapper;
		private final BiPredicate<Particle, T> combinedResource;

		SecondaryResource(Class<T> particleClass, ParticleToRRIMapper<Particle> rriMapper, BiPredicate<Particle, T> combinedResource) {
			this.particleClass = particleClass;
			this.rriMapper = rriMapper;
			this.combinedResource = combinedResource;
		}
	}

	private final Map<Class<? extends Particle>, ParticleToRRIMapper<Particle>> indexedParticles;
	private final Map<Class<? extends Particle>, SecondaryResource<? extends Particle>> secondary;

	public RRIParticleProcedureBuilder() {
		this.indexedParticles = new HashMap<>();
		this.secondary = new HashMap<>();
	}

	public <T extends Particle> RRIParticleProcedureBuilder add(Class<T> particleClass, ParticleToRRIMapper<T> indexedParticle) {
		if (this.indexedParticles.containsKey(particleClass)) {
			throw new IllegalStateException(particleClass + " already registered as a resource.");
		}

		this.indexedParticles.put(particleClass, p -> indexedParticle.index((T) p));
		return this;
	}

	public <T extends Particle, U extends Particle> RRIParticleProcedureBuilder add(
		Class<T> particleClass0, ParticleToRRIMapper<T> indexedParticle0,
		Class<U> particleClass1, ParticleToRRIMapper<U> indexedParticle1,
		BiPredicate<T, U> combinedResource
	) {
		if (this.indexedParticles.containsKey(particleClass0)) {
			throw new IllegalStateException(particleClass0 + " already registered as a resource.");
		}

		this.indexedParticles.put(particleClass0, p -> indexedParticle0.index((T) p));
		this.secondary.put(particleClass0, new SecondaryResource<>(
			particleClass1, p -> indexedParticle1.index((U) p), (p, t) -> combinedResource.test((T) p, t)));
		return this;
	}

	public ParticleProcedure build() {
		return new ParticleProcedure() {
			@Override
			public boolean inputExecute(Particle input, AtomMetadata metadata, Stack<Pair<Particle, Object>> outputs) {
				RRIParticle rriParticle = (RRIParticle) input;
				if (outputs.empty()) {
					return false;
				}

				Pair<Particle, Object> top = outputs.peek();
				Particle toParticle = top.getFirst();
				ParticleToRRIMapper<Particle> mapper = indexedParticles.get(toParticle.getClass());
				if (mapper == null) {
					return false;
				}

				if (!mapper.index(toParticle).equals(rriParticle.getRri())) {
					return false;
				}

				if (!metadata.isSignedBy(rriParticle.getRri().getAddress())) {
					return false;
				}

				outputs.pop();

				SecondaryResource secondaryResource = secondary.get(toParticle.getClass());
				if (secondaryResource != null) {
					if (outputs.empty()) {
						return false;
					}

					Pair<Particle, Object> top2 = outputs.peek();
					Particle toParticle2 = top2.getFirst();
					if (!toParticle2.getClass().equals(secondaryResource.particleClass)) {
						return false;
					}

					if (!secondaryResource.rriMapper.index(toParticle2).equals(rriParticle.getRri())) {
						return false;
					}

					if (!secondaryResource.combinedResource.test(toParticle, toParticle2)) {
						return false;
					}

					outputs.pop();
				}

				return true;
			}

			@Override
			public boolean outputExecute(Particle output, AtomMetadata metadata) {
																			   return false;
																							}
		};
	}
}
