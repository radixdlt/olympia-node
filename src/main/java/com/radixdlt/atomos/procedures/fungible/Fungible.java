package com.radixdlt.atomos.procedures.fungible;

import com.google.common.collect.ImmutableList;
import com.radixdlt.atoms.Particle;
import com.radixdlt.utils.UInt256;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

/**
 * A fungible Particle and a relevant amount
 */
// @PackageLocalForTest
public final class Fungible {
	// the particle itself
	private final Particle particle;

	// the class of that particle
	private final Class<? extends Particle> particleClass;

	// relevant amount for computations
	private final UInt256 amount;

	// index within the container, i.e. particle group
	private final int index;

	Fungible(Particle particle, Class<? extends Particle> particleClass, UInt256 amount, int index) {
		if (index < 0) {
			throw new IllegalArgumentException("Index must be > 0 but was " + index);
		}

		this.particle = Objects.requireNonNull(particle);
		this.particleClass = Objects.requireNonNull(particleClass);
		this.amount = Objects.requireNonNull(amount);
		this.index = index;
	}

	Fungible(Particle particle, UInt256 amount, int index) {
		this(particle, particle == null ? null : particle.getClass(), amount, index);
	}

	Fungible withAmount(UInt256 newAmount) {
		return new Fungible(this.particle, this.particleClass, newAmount, this.index);
	}

	Fungible subtract(Fungible other) {
		return new Fungible(this.particle, this.particleClass, this.amount.subtract(other.amount), this.index);
	}

	boolean isZero() {
		return this.amount.isZero();
	}

	public Class<? extends Particle> getParticleClass() {
		return this.particleClass;
	}

	public Particle getParticle() {
		return this.particle;
	}

	UInt256 getAmount() {
		return amount;
	}

	int getIndex() {
		return this.index;
	}

	String identifierString() {
		return String.format("%d:%s", this.index, this.particle.getHID());
	}

	@Override
	public String toString() {
		return String.format("%d:%s:%s:%s", this.index, this.particle.getHID(), this.particleClass.getSimpleName(), this.amount);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Fungible fungible = (Fungible) o;
		return index == fungible.index &&
			Objects.equals(particle, fungible.particle) &&
			Objects.equals(particleClass, fungible.particleClass) &&
			Objects.equals(amount, fungible.amount);
	}

	@Override
	public int hashCode() {
		return Objects.hash(particle, particleClass, amount, index);
	}

	public MutableFungible asMutable() {
		return new MutableFungible(this, this.amount);
	}

	/**
	 * Get the absolute difference between two sets of Fungibles
	 */
	static Stream<Fungible> diff(ImmutableList<Fungible> fungibles1, ImmutableList<Fungible> fungibles2) {
		Objects.requireNonNull(fungibles1, "fungibles1 is required");
		Objects.requireNonNull(fungibles2, "fungibles2 is required");

		BinaryOperator<UInt256> diffFunction = (a, b) -> {
			if (a == null) { return b; }
			else if (b == null) { return a; }

			if (a.compareTo(b) > 0) {
				return a.subtract(b);
			} else {
				return b.subtract(a);
			}
		};

		Set<Particle> allParticles = new HashSet<>();
		Map<Particle, MutableFungible> amounts1 = extractAmounts(fungibles1, allParticles);
		Map<Particle, MutableFungible> amounts2 = extractAmounts(fungibles2, allParticles);

		return allParticles.stream()
			.map(particle -> {
				final MutableFungible fungible1 = amounts1.get(particle);
				final MutableFungible fungible2 = amounts2.get(particle);
				final UInt256 diffAmount = diffFunction.apply(
					fungible1 != null ? fungible1.getAmount() : null,
					fungible2 != null ? fungible2.getAmount() : null);
				final MutableFungible existingFungible = fungible1 != null ? fungible1 : fungible2;
				existingFungible.setAmount(diffAmount);
				return existingFungible.asFungible();
			})
			.filter(f -> !f.isZero());
	}

	private static Map<Particle, MutableFungible> extractAmounts(ImmutableList<Fungible> fungibles, Set<Particle> allParticles) {
		Map<Particle, MutableFungible> amounts1 = new HashMap<>();
		for (Fungible fungible : fungibles) {
			amounts1.compute(fungible.getParticle(), (particle, amount) -> {
				if (amount == null) {
					return fungible.asMutable();
				}
				amount.add(fungible.getAmount());
				return amount;
			});
			allParticles.add(fungible.getParticle());
		}
		return amounts1;
	}
}
