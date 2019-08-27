package com.radixdlt.atoms;

import com.google.common.base.Suppliers;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A pointer into an atom
 * TODO: cache objects as they are used often
 */
public final class DataPointer {
	private final int particleGroupIndex;
	private final int particleIndex;
	private final Supplier<String> pointerToIssue;

	DataPointer(int particleGroupIndex, int particleIndex) {
		if (particleGroupIndex < -1) {
			throw new IllegalArgumentException("Particle group index must be >= -1.");
		}

		if (particleIndex < -1) {
			throw new IllegalArgumentException("Particle index must be >= -1.");
		}

		if (particleGroupIndex < 0) {
			if (particleIndex >= 0) {
			throw new IllegalArgumentException("Particle index must be included with a valid particle group index");
		}
		}

		this.particleGroupIndex = particleGroupIndex;
		this.particleIndex = particleIndex;
		this.pointerToIssue = Suppliers.memoize(() -> {
			StringBuilder stringBuilder = new StringBuilder("#");

			if (particleGroupIndex >= 0) {
				stringBuilder.append("/particleGroups/");
				stringBuilder.append(particleGroupIndex);
			}

			if (particleIndex >= 0) {
				stringBuilder.append("/particles/");
				stringBuilder.append(particleIndex);
			}

			return stringBuilder.toString();
		});
	}

	public static DataPointer ofParticleGroup(int particleGroupIndex) {
		return new DataPointer(particleGroupIndex, -1);
	}

	public static DataPointer ofParticle(int particleGroupIndex, int particleIndex) {
		return new DataPointer(particleGroupIndex, particleIndex);
	}

	public static DataPointer ofAtom() {
		return new DataPointer(-1, -1);
	}

	public int getParticleGroupIndex() {
		return particleGroupIndex;
	}

	public int getParticleIndex() {
		return particleIndex;
	}

	@Override
	public String toString() {
		return pointerToIssue.get();
	}

	@Override
	public int hashCode() {
		return Objects.hash(particleGroupIndex, particleIndex);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof DataPointer)) {
			return false;
		}
		DataPointer p = (DataPointer) o;
		return p.particleIndex == particleIndex && p.particleGroupIndex == particleGroupIndex;
	}
}
