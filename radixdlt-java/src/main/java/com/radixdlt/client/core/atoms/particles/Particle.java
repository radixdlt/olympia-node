package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.core.address.EUID;
import java.util.Set;

public interface Particle {
	Spin getSpin();
	Set<EUID> getDestinations();

}
