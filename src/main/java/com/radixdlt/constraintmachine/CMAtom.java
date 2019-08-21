package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.ECSignature;
import java.util.Set;

/**
 * An atom processed by a constraint machine with write destinations
 * TODO: Refactor and Remove ImmutableAtom. Currently too deeply embedded to be able to cleanly remove it.
 */
public final class CMAtom {
	private final ImmutableAtom atom;
	private final ImmutableList<CMParticle> cmParticles;
	private final ImmutableMap<EUID, ECSignature> signatures;
	private final ImmutableMap<String, String> metaData;
	private final ImmutableSet<EUID> destinations;
	private final ImmutableSet<Long> shards;

	public CMAtom(
		ImmutableAtom atom,
		ImmutableList<CMParticle> cmParticles
	) {
		this.atom = atom;
		this.cmParticles = cmParticles;
		this.signatures = ImmutableMap.copyOf(atom.getSignatures());
		this.metaData = ImmutableMap.copyOf(atom.getMetaData());
		this.destinations = cmParticles.stream()
			.map(CMParticle::getParticle)
			.map(Particle::getDestinations)
			.flatMap(Set::stream)
			.collect(ImmutableSet.toImmutableSet());
		this.shards = this.destinations.stream().map(EUID::getShard).collect(ImmutableSet.toImmutableSet());
	}

	public ImmutableMap<String, String> getMetaData() {
		return metaData;
	}

	public ImmutableSet<EUID> getDestinations() {
		return destinations;
	}

	public ImmutableList<CMParticle> getParticles() {
		return cmParticles;
	}

	public ImmutableMap<EUID, ECSignature> getSignatures() {
		return signatures;
	}

	public ImmutableSet<Long> getShards() {
		return shards;
	}

	public ImmutableAtom getAtom() {
		return atom;
	}
}
