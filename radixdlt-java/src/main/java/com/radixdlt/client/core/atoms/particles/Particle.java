package com.radixdlt.client.core.atoms.particles;

import java.util.Set;

import com.radixdlt.client.core.crypto.ECPublicKey;

public abstract class Particle {
	public abstract Spin getSpin();
	public abstract Set<ECPublicKey> getAddresses();
}
