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
			this.indexedParticles.put(particleClass, p -> indexedParticle.index((T) p));
			return this;
		}

		public RRIConstraintProcedure build() {
			return new RRIConstraintProcedure(indexedParticles);
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
					if (this.check((RRIParticle) p, metadata, outputs)) {
						outputs.pop();
					} else {
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
