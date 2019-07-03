package com.radixdlt.atoms;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * A particle group with knowledge of it's index within an atom.
 */
public final class IndexedParticleGroup {
	private final ParticleGroup particleGroup;
	private final int particleGroupIndex;

	public IndexedParticleGroup(ParticleGroup particleGroup, int particleGroupIndex) {
		if (particleGroupIndex < 0) {
			throw new IllegalArgumentException("particleGroupIndex must be >= 0.");
		}

		this.particleGroup = Objects.requireNonNull(particleGroup);
		this.particleGroupIndex = particleGroupIndex;
	}

	public DataPointer getDataPointer() {
		return DataPointer.ofParticleGroup(particleGroupIndex);
	}

	public ParticleGroup getParticleGroup() {
		return particleGroup;
	}

	public Stream<IndexedSpunParticle> indexedSpunParticles() {
		return particleGroup.spunParticlesWithIndex((sp, i) -> new IndexedSpunParticle(sp, DataPointer.ofParticle(particleGroupIndex, (int) i)));
	}
}
