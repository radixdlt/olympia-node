package com.radixdlt.tempo.discovery;

public interface AtomDiscoverer {
	void addListener(AtomDiscoveryListener listener);

	void removeListener(AtomDiscoveryListener listener);
}
