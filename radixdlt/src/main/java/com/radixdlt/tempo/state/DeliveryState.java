package com.radixdlt.tempo.state;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.tempo.TempoState;

import java.util.Set;

public final class DeliveryState implements TempoState {
	private final Set<AID> ongoingDeliveries;

	public DeliveryState(Set<AID> ongoingDeliveries) {
		this.ongoingDeliveries = ongoingDeliveries;
	}

	public Set<AID> getOngoingDeliveries() {
		return ongoingDeliveries;
	}

	public boolean contains(AID aid) {
		return ongoingDeliveries.contains(aid);
	}

	public static DeliveryState initial() {
		return new DeliveryState(ImmutableSet.of());
	}
}
