package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.crypto.Hash;
import java.util.Set;

/**
 * An atom processed by a constraint machine with write destinations
 */
public final class CMInstruction {
	private final Hash atomHash;
	private final ImmutableList<CMParticle> cmParticles;
	private final ImmutableList<ImmutableList<Particle>> particlePushes;
	private final ImmutableMap<EUID, ECSignature> signatures;
	private final ImmutableSet<EUID> destinations;
	private final ImmutableSet<Long> shards;

	public CMInstruction(
		ImmutableAtom atom,
		ImmutableList<CMParticle> cmParticles
	) {
		this.atomHash = atom.getHash();
		this.cmParticles = cmParticles;
		this.particlePushes = atom.particleGroups()
			.map(pg -> pg.spunParticles().map(SpunParticle::getParticle).collect(ImmutableList.toImmutableList()))
			.collect(ImmutableList.toImmutableList());
		this.signatures = ImmutableMap.copyOf(atom.getSignatures());
		this.destinations = cmParticles.stream()
			.map(CMParticle::getParticle)
			.map(Particle::getDestinations)
			.flatMap(Set::stream)
			.collect(ImmutableSet.toImmutableSet());
		this.shards = this.destinations.stream().map(EUID::getShard).collect(ImmutableSet.toImmutableSet());
	}

	public ImmutableList<ImmutableList<Particle>> getParticlePushes() {
		return particlePushes;
	}

	public Hash getAtomHash() {
		return atomHash;
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
}
