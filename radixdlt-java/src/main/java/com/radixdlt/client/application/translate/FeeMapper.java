package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.List;

public interface FeeMapper {
	List<SpunParticle> map(List<SpunParticle> particles, RadixUniverse universe, ECPublicKey key);
}
