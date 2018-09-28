package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import java.util.Set;

public interface Particle {
	long getSpin();
	Set<EUID> getDestinations();
}
