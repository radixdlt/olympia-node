package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.Set;

public interface Particle {
	Spin getSpin();
	Set<ECPublicKey> getAddresses();
}
