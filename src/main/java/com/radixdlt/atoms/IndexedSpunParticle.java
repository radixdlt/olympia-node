package com.radixdlt.atoms;

import java.util.Objects;

/**
 * A spun particle with knowledge of it's location within an atom.
 */
public final class IndexedSpunParticle {
	private final SpunParticle spunParticle;
	private final DataPointer dataPointer;

	public IndexedSpunParticle(SpunParticle spunParticle, DataPointer dataPointer) {
		this.spunParticle = Objects.requireNonNull(spunParticle);
		this.dataPointer = Objects.requireNonNull(dataPointer);
	}

	public SpunParticle getSpunParticle() {
		return spunParticle;
	}

	public DataPointer getDataPointer() {
		return dataPointer;
	}
}
