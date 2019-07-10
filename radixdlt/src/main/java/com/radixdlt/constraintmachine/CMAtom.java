package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.radix.atoms.Atom;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.EUID;
import java.util.Set;

/**
 * An atom processed by a constraint machine with write destinations
 */
public final class CMAtom {
	private final Atom atom;
	private final ImmutableList<CMParticle> cmParticles;
	private final ImmutableMap<String, Object> computed;
	private final ImmutableSet<EUID> destinations;
	private final ImmutableSet<Long> shards;

	CMAtom(Atom atom, ImmutableList<CMParticle> cmParticles, ImmutableMap<String, Object> computed) {
		this.atom = atom;
		this.cmParticles = cmParticles;
		this.computed = computed;
		this.destinations = cmParticles.stream()
			.map(CMParticle::getParticle)
			.map(Particle::getDestinations)
			.flatMap(Set::stream)
			.collect(ImmutableSet.toImmutableSet());
		this.shards = this.destinations.stream().map(EUID::getShard).collect(ImmutableSet.toImmutableSet());
	}

	public <T> T getComputedOrError(String key, Class<T> c) {
		Object result = this.computed.get(key);
		if (result == null) {
			throw new NullPointerException("Compute key " + key + " does not exist");
		}

		return c.cast(result);
	}

	public ImmutableSet<EUID> getDestinations() {
		return destinations;
	}

	public ImmutableList<CMParticle> getParticles() {
		return cmParticles;
	}

	public ImmutableSet<Long> getShards() {
		return shards;
	}

	public Atom getAtom() {
		return atom;
	}
}
