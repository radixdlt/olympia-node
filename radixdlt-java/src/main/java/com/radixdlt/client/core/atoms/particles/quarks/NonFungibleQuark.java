package com.radixdlt.client.core.atoms.particles.quarks;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;

/**
 * A quark that makes a particle non fungible: only one particle with a given ID of its type can exist.
 */
@SerializerId2("NONFUNGIBLEQUARK")
public final class NonFungibleQuark extends Quark {
	@JsonProperty("uid")
	@DsonOutput(DsonOutput.Output.ALL)
	private EUID uid;

	private NonFungibleQuark() {
	}

	public NonFungibleQuark(EUID uid) {
		if (uid == null || uid.equals(EUID.ZERO)) {
			throw new IllegalArgumentException("uid is null or zero");
		}

		this.uid = uid;
	}

	public EUID getUid() {
		return uid;
	}
}
