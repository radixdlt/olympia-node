package com.radixdlt.tempo.delivery;

public interface AtomDeliverer {
	void addListener(AtomDeliveryListener deliveryListener);

	void removeListener(AtomDeliveryListener deliveryListener);
}
