package com.radixdlt.constraintmachine;

import com.radixdlt.atoms.ImmutableAtom;
import java.util.function.Predicate;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.crypto.CryptoException;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Helper for implementing {@link AtomMetadata} given an Atom of interest
 */
public class AtomMetadataFromAtom implements AtomMetadata {
	private final ImmutableAtom atom;
	private final Map<RadixAddress, Boolean> isSignedByCache = new HashMap<>();

	public AtomMetadataFromAtom(ImmutableAtom atom) {
		this.atom = Objects.requireNonNull(atom, "atom is required");
	}

	@Override
	public boolean isSignedBy(RadixAddress address) {
		return this.isSignedByCache.computeIfAbsent(address, this::verifySignedWith);
	}

	@Override
	public boolean contains(Predicate<Particle> predicate) {
		return this.atom.particleGroups()
			.flatMap(ParticleGroup::spunParticles)
			.map(SpunParticle::getParticle)
			.anyMatch(predicate);
	}

	private boolean verifySignedWith(RadixAddress address) {
		try {
			return atom.verify(address.getKey());
		} catch (CryptoException e) {
			return false;
		}
	}
}
