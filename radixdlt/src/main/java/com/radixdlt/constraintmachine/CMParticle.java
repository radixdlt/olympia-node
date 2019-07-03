package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import java.util.Objects;
import java.util.stream.Stream;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.IndexedSpunParticle;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;

/**
 * A particle processed by a Constraint Machine
 */
public class CMParticle {
	private final Particle particle;
	private final ImmutableList<IndexedSpunParticle> spunParticles;

	CMParticle(Particle particle, ImmutableList<IndexedSpunParticle> spunParticles) {
		this.particle = particle;
		this.spunParticles = Objects.requireNonNull(spunParticles);
	}

	public Particle getParticle() {
		return particle;
	}

	public Stream<Spin> nextSpins() {
		return spunParticles.stream()
			.map(IndexedSpunParticle::getSpunParticle)
			.map(SpunParticle::getSpin);
	}

	public DataPointer getDataPointer() {
		return spunParticles.get(0).getDataPointer();
	}

	public Spin getNextSpin() {
		return spunParticles.get(0).getSpunParticle().getSpin();
	}
}
