package com.radixdlt.atomos.procedures;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atomos.mapper.ParticleToRRIMapper;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ConstraintProcedure;
import com.radixdlt.constraintmachine.ProcedureError;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

/**
 * Low level code for Indexed Constraints which manages new atoms,
 * state and when to re-check that constraints have been maintained
 * given new atoms
 */
public final class RRIConstraintProcedure implements ConstraintProcedure {
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

	RRIConstraintProcedure(
		Map<Class<? extends Particle>, ParticleToRRIMapper<Particle>> indexedParticles,
		Map<Class<? extends Particle>, SecondaryResource<? extends Particle>> secondary
	) {
		this.indexedParticles = ImmutableMap.copyOf(indexedParticles);
		this.secondary = secondary;
	}

	public static final class Builder {
		private final Map<Class<? extends Particle>, ParticleToRRIMapper<Particle>> indexedParticles;
		private final Map<Class<? extends Particle>, SecondaryResource<? extends Particle>> secondary;

		public Builder() {
			this.indexedParticles = new HashMap<>();
			this.secondary = new HashMap<>();
		}

		public <T extends Particle> Builder add(Class<T> particleClass, ParticleToRRIMapper<T> indexedParticle) {
			if (this.indexedParticles.containsKey(particleClass)) {
				throw new IllegalStateException(particleClass + " already registered as a resource.");
			}

			this.indexedParticles.put(particleClass, p -> indexedParticle.index((T) p));
			return this;
		}

		public <T extends Particle, U extends Particle> Builder add(
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

		public RRIConstraintProcedure build() {
			return new RRIConstraintProcedure(indexedParticles, secondary);
		}
	}

	private boolean check(RRIParticle input, AtomMetadata metadata, Stack<Pair<Particle, Void>> outputs) {
		if (outputs.empty()) {
			return false;
		}

		Pair<Particle, Void> top = outputs.peek();
		Particle toParticle = top.getFirst();
		ParticleToRRIMapper<Particle> mapper = indexedParticles.get(toParticle.getClass());
		if (mapper == null) {
			return false;
		}

		if (!mapper.index(toParticle).equals(input.getRri())) {
			return false;
		}

		if (!metadata.isSignedBy(input.getRri().getAddress())) {
			return false;
		}

		outputs.pop();

		SecondaryResource secondaryResource = secondary.get(toParticle.getClass());
		if (secondaryResource != null) {
			if (outputs.empty()) {
				return false;
			}

			Pair<Particle, Void> top2 = outputs.peek();
			Particle toParticle2 = top2.getFirst();
			if (!toParticle2.getClass().equals(secondaryResource.particleClass)) {
				return false;
			}

			if (!secondaryResource.rriMapper.index(toParticle2).equals(input.getRri())) {
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
	public Stream<ProcedureError> validate(ParticleGroup group, AtomMetadata metadata) {
		final Stack<Pair<Particle, Void>> inputs = new Stack<>();
		final Stack<Pair<Particle, Void>> outputs = new Stack<>();

		for (int i = group.getParticleCount() - 1; i >= 0; i--) {
			SpunParticle sp = group.getSpunParticle(i);
			Particle p = sp.getParticle();
			if (sp.getSpin() == Spin.DOWN) {
				if (p instanceof RRIParticle) {
					if (!this.check((RRIParticle) p, metadata, outputs)) {
						inputs.push(Pair.of(p, null));
					}
				} else if (this.indexedParticles.containsKey(p.getClass())) {
					inputs.push(Pair.of(p, null));
				}
			} else if (sp.getSpin() == Spin.UP && (this.indexedParticles.containsKey(p.getClass())) || p instanceof RRIParticle) {
				outputs.push(Pair.of(p, null));
			}
		}

		if (!inputs.empty()) {
			return Stream.of(ProcedureError.of("Input stack not empty"));
		} else if (!outputs.empty()) {
			return Stream.of(ProcedureError.of("Output stack not empty"));
		}

		return Stream.empty();
	}
}
