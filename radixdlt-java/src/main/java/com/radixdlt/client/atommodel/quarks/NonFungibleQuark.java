package com.radixdlt.client.atommodel.quarks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.particles.Quark;
import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;

import java.util.Objects;

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

		this.uid = Objects.requireNonNull(uid, "uid is required");
	}

	public EUID getUid() {
		return uid;
	}
}
