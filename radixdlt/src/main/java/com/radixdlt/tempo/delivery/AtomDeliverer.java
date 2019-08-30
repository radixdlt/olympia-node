package com.radixdlt.tempo.delivery;

/**
 * A deliverer and source of {@link com.radixdlt.tempo.TempoAtom}s
 */
public interface AtomDeliverer {
	/**
	 * Adds a listener for delivered {@link com.radixdlt.tempo.TempoAtom} s
	 * @param deliveryListener The listener
	 */
	void addListener(AtomDeliveryListener deliveryListener);

	/**
	 * Removes a listener for delivered {@link com.radixdlt.tempo.TempoAtom} s
	 * @param deliveryListener The listener
	 */
	void removeListener(AtomDeliveryListener deliveryListener);
}
