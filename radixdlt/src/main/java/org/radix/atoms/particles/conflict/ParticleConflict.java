package org.radix.atoms.particles.conflict;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.common.AID;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.collections.WireableSet;
import org.radix.validation.ValidatableObject;

import java.util.Collections;
import java.util.HashSet;
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
	private WireableSet<AID> atomIds;

	public ParticleConflict(DataPointer dataPointer, Set<AID> atomIds) {
		super();
		this.dataPointer = dataPointer;
		this.atomIds = new WireableSet<>(atomIds);
	}

	@Override
	public String toString() {
		return this.getClass().toString() + ": " + dataPointer.toString();
	}

	public DataPointer getDataPointer() {
		return dataPointer;
	}

	public Set<AID> getAtomIds() {
		return Collections.unmodifiableSet(new HashSet<>(this.atomIds));
	}

}