package com.radixdlt.atoms;

import com.google.common.base.Suppliers;
import java.util.Objects;
import java.util.function.Supplier;
import org.radix.atoms.Atom;

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

	public SpunParticle getParticleFrom(Atom atom) {
		if (particleIndex < 0) {
			throw new IllegalArgumentException("Pointer does not point to a particle");
		}

		this.validateExists(atom);

		return atom.getParticleGroup(this.particleGroupIndex).getSpunParticle(particleIndex);
	}

	public void validateExists(Atom atom) {
		if (particleGroupIndex < 0) {
			return;
		}

		if (particleGroupIndex >= atom.getParticleGroupCount()) {
			throw new IllegalArgumentException("Particle group index " + particleGroupIndex
				+ " is >= atom particle group count " + atom.getParticleGroupCount());
		}

		if (particleIndex < 0) {
			return;
		}

		if (particleIndex >= atom.getParticleGroup(particleGroupIndex).getParticleCount()) {
			throw new IllegalArgumentException("Particle index " + particleIndex + " is >= particle group particle count "
				+ atom.getParticleGroup(particleGroupIndex).getParticleCount());
		}
	}

	/**
	 * Retrieve the data pointer of a spun particle in an atom
	 * @param spunParticle the spun particle to get the data pointer of
	 * @param atom the atom the spun particle is within
	 * @return a pointer into the atom pointing to the spun particle given
	 */
	public static DataPointer ofParticleInAtom(SpunParticle spunParticle, Atom atom) {
		ParticleGroup particleGroup = atom.particleGroups()
			.filter(pg -> pg.contains(spunParticle))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(String.format(
				"Could not get validation pointer for %s in atom %s, no containing group found",
				spunParticle.getParticle().getHID(), atom.getAID())));
		int groupIndex = atom.indexOfParticleGroup(particleGroup);
		int particleInGroupIndex = particleGroup.indexOfSpunParticle(spunParticle);

		return DataPointer.ofParticle(groupIndex, particleInGroupIndex);
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
