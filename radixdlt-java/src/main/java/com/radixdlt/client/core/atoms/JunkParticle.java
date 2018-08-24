package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.crypto.ECKeyPair;
import java.util.Set;
import java.util.stream.Collectors;

public class JunkParticle extends Particle {
	private final byte[] junk;

	public JunkParticle(byte[] junk, Set<RadixAddress> owners) {
		super(
			owners.stream().map(RadixAddress::getUID).collect(Collectors.toSet()),
			owners.stream().map(RadixAddress::getPublicKey).map(ECKeyPair::new).collect(Collectors.toSet())
		);
		this.junk = junk;
	}
}
