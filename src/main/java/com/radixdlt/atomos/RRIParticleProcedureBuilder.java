package com.radixdlt.atomos;

import com.radixdlt.atomos.mapper.ParticleToRRIMapper;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ParticleProcedure;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;
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

	private enum ProcedureResult {
		POP_INPUT,
		POP_OUTPUT,
		POP_INPUT_OUTPUT,
		ERROR
	}

	public ParticleProcedure build() {
		return new ParticleProcedure() {

			private ProcedureResult execute(
				Particle inputParticle,
				AtomicReference<Object> inputData,
				Particle outputParticle,
				AtomicReference<Object> outputData,
				AtomMetadata metadata
			) {
				RRIParticle rriParticle = (RRIParticle) inputParticle;

				if (inputData.get() != null) {
					Pair<SecondaryResource, Particle> data = (Pair<SecondaryResource, Particle>) inputData.get();
					SecondaryResource secondaryResource = data.getFirst();
					if (!secondaryResource.particleClass.equals(outputParticle.getClass())) {
						return ProcedureResult.ERROR;
					}

					if (!secondaryResource.rriMapper.index(outputParticle).equals(rriParticle.getRri())) {
						return ProcedureResult.ERROR;
					}

					if (!secondaryResource.combinedResource.test(data.getSecond(), outputParticle)) {
						return ProcedureResult.ERROR;
					}
				} else {
					ParticleToRRIMapper<Particle> mapper = indexedParticles.get(outputParticle.getClass());
					if (mapper == null) {
						return ProcedureResult.ERROR;
					}

					if (!mapper.index(outputParticle).equals(rriParticle.getRri())) {
						return ProcedureResult.ERROR;
					}

					if (!metadata.isSignedBy(rriParticle.getRri().getAddress())) {
						return ProcedureResult.ERROR;
					}

					SecondaryResource secondaryResource = secondary.get(outputParticle.getClass());
					if (secondaryResource != null) {
						inputData.set(Pair.of(secondaryResource, outputParticle));
						return ProcedureResult.POP_OUTPUT;
					}
				}

				return ProcedureResult.POP_INPUT_OUTPUT;
			}

			@Override
			public boolean inputExecute(Particle input, AtomMetadata metadata, Stack<Pair<Particle, Object>> outputs) {
				AtomicReference<Object> inputData = new AtomicReference<>();

				ProcedureResult action;
				do {
					if (outputs.empty()) {
						action = ProcedureResult.ERROR;
						break;
					}
					Pair<Particle, Object> top = outputs.pop();
					Particle output = top.getFirst();
					AtomicReference<Object> outputData = new AtomicReference<>(top.getSecond());
					action = execute(input, inputData, output, outputData, metadata);

					switch (action) {
						case POP_INPUT:
						case ERROR:
							outputs.push(Pair.of(output, outputData.get()));
					}
				} while (action.equals(ProcedureResult.POP_OUTPUT));

				return action != ProcedureResult.ERROR;
			}

			@Override
			public boolean outputExecute(Particle output, AtomMetadata metadata) {
				return false;
			}
		};
	}
}
