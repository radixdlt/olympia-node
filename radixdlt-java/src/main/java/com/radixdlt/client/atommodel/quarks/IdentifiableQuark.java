package com.radixdlt.client.atommodel.quarks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.particles.Quark;
import com.radixdlt.client.core.atoms.particles.RadixResourceIdentifer;
import java.util.Objects;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;

/**
 * A quark that makes a particle type uniquely identifiable: Only one UP particle with a URI
 * (defined by address + particleType + uniqueId) can exist at once.
 */
@SerializerId2("IDENTIFIABLEQUARK")
public final class IdentifiableQuark extends Quark {
	@JsonProperty("id")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixResourceIdentifer id;

	private IdentifiableQuark() {
		super();
	}

	public IdentifiableQuark(RadixResourceIdentifer id) {
		Objects.requireNonNull(id);
		this.id = id;
	}

	public RadixResourceIdentifer getId() {
		return id;
	}
}
