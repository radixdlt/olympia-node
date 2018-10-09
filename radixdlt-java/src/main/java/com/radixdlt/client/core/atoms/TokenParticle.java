package com.radixdlt.client.core.atoms;

import com.google.gson.annotations.SerializedName;
import com.radixdlt.client.application.objects.Token;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECKeyPair;
import java.util.Collections;
import java.util.Set;

public class TokenParticle implements Particle {
	private final Set<ECKeyPair> owners;
	@SerializedName("sub_units")
	private final long subUnits;
	@SerializedName("maximum_units")
	private final String iso;
	private final String label;
	private final String description;
	private final byte[] icon;
	private final EUID id;
	private final Spin spin;

	public TokenParticle(
		Set<ECKeyPair> owners,
		long subUnits,
		String iso,
		String label,
		String description,
		byte[] icon
	) {
		this.spin = Spin.UP;
		this.owners = owners;
		this.id = Token.calcEUID(iso);
		this.subUnits = subUnits;
		this.iso = iso;
		this.label = label;
		this.description = description;
		this.icon = icon;
	}

	// TODO: fix this to be an account
	public Set<EUID> getDestinations() {
		return Collections.singleton(id);
	}

	public Spin getSpin() {
		return spin;
	}
}
