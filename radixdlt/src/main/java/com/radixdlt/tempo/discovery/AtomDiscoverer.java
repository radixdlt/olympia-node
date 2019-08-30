package com.radixdlt.tempo.discovery;

/**
 * A discoverer and source of {@link com.radixdlt.common.AID}s
 */
public interface AtomDiscoverer {
	/**
	 * Adds a listener for discovered {@link com.radixdlt.common.AID}s
	 * @param listener The listener
	 */
	void addListener(AtomDiscoveryListener listener);

	/**
	 * Removes a listener for discovered {@link com.radixdlt.common.AID}s
	 * @param listener The listener
	 */
	void removeListener(AtomDiscoveryListener listener);
}
