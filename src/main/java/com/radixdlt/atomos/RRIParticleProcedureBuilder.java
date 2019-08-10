package com.radixdlt.atomos;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atomos.mapper.ParticleToRRIMapper;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ConstraintProcedure;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

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

	public ConstraintProcedure build() {
		return new ConstraintProcedure() {

			@Override
			public ImmutableSet<Pair<Class<? extends Particle>, Class<? extends Particle>>> supports() {
				return Stream.concat(
					indexedParticles.keySet().stream(),
					secondary.entrySet().stream().map(e -> e.getValue().particleClass)
				)
					.distinct()
					.map(c -> Pair.<Class<? extends Particle>, Class<? extends Particle>>of(RRIParticle.class, c))
					.collect(ImmutableSet.toImmutableSet());
			}

			@Override
			public boolean validateWitness(
				ProcedureResult result,
				Particle inputParticle,
				Particle outputParticle,
				AtomMetadata metadata
			) {
				RRIParticle rriParticle = (RRIParticle) inputParticle;
				switch (result) {
					case POP_OUTPUT:
						return true;
					case POP_INPUT_OUTPUT:
						return metadata.isSignedBy(rriParticle.getRri().getAddress());
					case POP_INPUT:
					default:
						throw new IllegalStateException();
				}
			}

			@Override
			public ProcedureResult execute(
				Particle inputParticle,
				AtomicReference<Object> inputData,
				Particle outputParticle,
				AtomicReference<Object> outputData
			) {
				RRIParticle rriParticle = (RRIParticle) inputParticle;

				if (inputData.get() != null) {
					Particle oldParticle = (Particle) inputData.get();
					SecondaryResource secondaryResource = secondary.get(oldParticle.getClass());
					if (!secondaryResource.particleClass.equals(outputParticle.getClass())) {
						return ProcedureResult.ERROR;
					}

					if (!secondaryResource.rriMapper.index(outputParticle).equals(rriParticle.getRri())) {
						return ProcedureResult.ERROR;
					}

					if (!secondaryResource.combinedResource.test(oldParticle, outputParticle)) {
						return ProcedureResult.ERROR;
					}
				} else {
					ParticleToRRIMapper<Particle> mapper = indexedParticles.get(outputParticle.getClass());

					if (!mapper.index(outputParticle).equals(rriParticle.getRri())) {
						return ProcedureResult.ERROR;
					}

					SecondaryResource secondaryResource = secondary.get(outputParticle.getClass());
					if (secondaryResource != null) {
						inputData.set(outputParticle);
						return ProcedureResult.POP_OUTPUT;
					}
				}

				return ProcedureResult.POP_INPUT_OUTPUT;
			}
		};
	}
}
