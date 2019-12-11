package org.radix.atoms.particles.conflict;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.validation.ValidatableObject;

import java.util.Set;

@SerializerId2("conflict.particle_conflict")
public final class ParticleConflict extends ValidatableObject {
	@Override
	public short VERSION() {
		return 100;
	}

	@JsonProperty("dataPointer")
	@DsonOutput(Output.ALL)
	private DataPointer dataPointer;

	@JsonProperty("atomIds")
	@DsonOutput(Output.ALL)
	private Set<AID> atomIds;

	public ParticleConflict(DataPointer dataPointer, Set<AID> atomIds) {
		super();
		this.dataPointer = dataPointer;
		this.atomIds = ImmutableSet.copyOf(atomIds);
	}

	@Override
	public String toString() {
		return this.getClass().toString() + ": " + dataPointer.toString();
	}

	public DataPointer getDataPointer() {
		return dataPointer;
	}

	public Set<AID> getAtomIds() {
		return this.atomIds;
	}

}