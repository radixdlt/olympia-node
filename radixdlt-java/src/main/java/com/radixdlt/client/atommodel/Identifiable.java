package com.radixdlt.client.atommodel;

import com.radixdlt.client.core.atoms.particles.RRI;

/**
 * Temporary interface for representing a particle which is indexed at an RRI
 */
public interface Identifiable {
	RRI getRRI();
}
