package com.radixdlt.client.atommodel.quarks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.particles.Quark;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;

/**
 * Specifies a uniqueness constraint on an atom. That is, only one atom can
 * contain this particle with this unique quark's byte array contents and its owner.
 */
@SerializerId2("UNIQUEQUARK")
public final class UniqueQuark extends Quark {
	@JsonProperty("unique")
	@DsonOutput(DsonOutput.Output.ALL)
	private byte[] unique;

	private UniqueQuark() {
	}

	public UniqueQuark(byte[] unique) {
		this.unique = unique;
	}

	public byte[] getUnique() {
		return this.unique;
	}
}

