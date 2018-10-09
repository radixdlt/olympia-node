package com.radixdlt.client.core.atoms;

import com.google.gson.annotations.SerializedName;
import com.radixdlt.client.application.objects.Token;
import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.crypto.ECKeyPair;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TokenParticle implements Particle {
	@SerializedName("sub_units")
	private final long subUnits;
	private final String iso;
	private final String name;
	private final String description;
	private final byte[] icon;
	private final EUID uid;
	private final Spin spin;
	private final List<AccountReference> addresses;

	public TokenParticle(
		AccountReference accountReference,
		String name,
		String iso,
		String description,
		long subUnits,
		byte[] icon
	) {
		this.addresses = Collections.singletonList(accountReference);
		this.spin = Spin.UP;
		this.uid = Token.calcEUID(iso);
		this.subUnits = subUnits;
		this.iso = iso;
		this.name = name;
		this.description = description;
		this.icon = icon;
	}

	// TODO: fix this to be an account
	public Set<EUID> getDestinations() {
		return Collections.singleton(uid);
	}

	public Spin getSpin() {
		return spin;
	}
}
