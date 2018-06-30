package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.crypto.ECKeyPair;
import org.bouncycastle.util.encoders.Base64;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class NullAtom extends Atom {
	public static class JunkParticle extends Particle {
		private final byte[] junk;

		public JunkParticle(byte[] junk, Set<RadixAddress> owners) {
			super(
				owners.stream().map(RadixAddress::getUID).collect(Collectors.toSet()),
				owners.stream().map(RadixAddress::getPublicKey).map(ECKeyPair::new).collect(Collectors.toSet())
			);
			this.junk = junk;
		}
	}

	NullAtom(Set<RadixAddress> owners, byte[] junk, long timestamp) {
		super(
			owners.stream().map(RadixAddress::getUID).collect(Collectors.toSet()),
			Collections.singletonList(new JunkParticle(junk, owners)),
			timestamp
		);
	}

	public String getJunk() {
		return Base64.toBase64String(((JunkParticle) getParticles().get(0)).junk);
	}
}
