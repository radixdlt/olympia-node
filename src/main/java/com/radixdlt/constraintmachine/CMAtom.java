package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.crypto.Hash;
import java.util.Set;

/**
 * An atom processed by a constraint machine with write destinations
 * TODO: Refactor and Remove ImmutableAtom. Currently too deeply embedded to be able to cleanly remove it.
 */
public final class CMAtom {
	public static final String METADATA_TIMESTAMP_KEY = "timestamp";
	public static final String METADATA_POW_NONCE_KEY = "powNonce";

	// TODO: Remove
	private final ImmutableAtom atom;


	private final Hash powFeeHash;
	private final Hash atomHash;
	private final AID aid;
	private final ImmutableList<CMParticle> cmParticles;
	private final ImmutableList<ImmutableList<Particle>> particlePushes;
	private final ImmutableMap<EUID, ECSignature> signatures;
	private final ImmutableMap<String, String> metaData;
	private final ImmutableSet<EUID> destinations;
	private final ImmutableSet<Long> shards;

	public CMAtom(
		ImmutableAtom atom,
		ImmutableList<CMParticle> cmParticles
	) {
		this.atom = atom;

		this.atomHash = atom.getHash();
		this.powFeeHash = atom.copyExcludingMetadata(ImmutableAtom.METADATA_POW_NONCE_KEY).getHash();
		this.aid = atom.getAID();
		this.cmParticles = cmParticles;
		this.particlePushes = atom.particleGroups()
			.map(pg -> pg.spunParticles().map(SpunParticle::getParticle).collect(ImmutableList.toImmutableList()))
			.collect(ImmutableList.toImmutableList());
		this.signatures = ImmutableMap.copyOf(atom.getSignatures());
		this.metaData = ImmutableMap.copyOf(atom.getMetaData());
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

	public Hash getPowFeeHash() {
		return powFeeHash;
	}

	/**
	 * Convenience method to retrieve timestamp
	 *
	 * @return The timestamp in milliseconds since epoch
	 */
	public long getTimestamp() {
		// TODO Not happy with this error handling as it moves some validation work into the atom data. See RLAU-951
		try {
			return Long.parseLong(this.metaData.get(METADATA_TIMESTAMP_KEY));
		} catch (NumberFormatException e) {
			return Long.MIN_VALUE;
		}
	}

	public AID getAID() {
		return aid;
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
