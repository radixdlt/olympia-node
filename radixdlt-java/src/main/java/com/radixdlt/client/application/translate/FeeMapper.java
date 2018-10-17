package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.List;

public interface FeeMapper {
	List<Particle> map(List<Particle> particles, RadixUniverse universe, ECPublicKey key);
}
