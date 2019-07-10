package org.radix.atoms;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.time.TemporalProof;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An atom with a mutable temporal proof
 */
@SerializerId2("radix.atom")
public final class Atom extends ImmutableAtom {

	/**
	 * The TemporalProof associated with this Atom. Null if not yet generated.
	 */
	@JsonProperty("temporalProof")
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	private TemporalProof temporalProof = null;

	public Atom() {
		super();
	}

	public Atom(long timestamp) {
		super(timestamp);
	}

	public Atom(long timestamp, Map<String, String> metadata) {
		super(timestamp, metadata);
	}

	Atom(List<ParticleGroup> particleGroups, Map<EUID, ECSignature> signatures, Map<String, String> metaData) {
		super(particleGroups, signatures, metaData);
	}


	// FIXME: Calling Atom.getTemporalProof() calls getHash().
	// Unfortunately getHash() caches it's values, so all relevant (for hashing purposes)
	// data *must* be added to the Atom before getTemporalProof() is called.
	public final TemporalProof getTemporalProof() {
		if (this.temporalProof == null) {
			this.temporalProof = new TemporalProof(this.getAID());
		}

		return this.temporalProof;
	}

	public final void setTemporalProof(TemporalProof temporalProof) {
		this.temporalProof = temporalProof;
	}

	/**
	 * Get a copy of this Atom with certain metadata filtered out
	 * @param keysToExclude The keys to exclude
	 * @return The copied atom with the filtered metadata
	 */
	public Atom copyExcludingMetadata(String... keysToExclude) {
		Objects.requireNonNull(keysToExclude, "keysToRetain is required");

		ImmutableSet<String> keysToExcludeSet = ImmutableSet.copyOf(keysToExclude);
		Map<String, String> filteredMetaData = this.metaData.entrySet().stream()
			.filter(metaDataEntry -> !keysToExcludeSet.contains(metaDataEntry.getKey()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		return new Atom(this.particleGroups, this.signatures, filteredMetaData);
	}


	@Override
	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}

		if (o == this) {
			return true;
		}

		if (getClass().isInstance(o) && getHash().equals(((Atom) o).getHash())) {
			return true;
		}

		return false;
	}

	@Override
	public int hashCode() {
		return getHash().hashCode();
	}
}
