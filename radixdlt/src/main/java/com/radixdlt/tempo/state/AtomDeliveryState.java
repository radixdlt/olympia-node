package com.radixdlt.tempo.state;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoState;

import java.util.Map;

public final class AtomDeliveryState implements TempoState {
	private final Map<AID, EUID> pendingDeliveries;

	public AtomDeliveryState(Map<AID, EUID> pendingDeliveries) {
		this.pendingDeliveries = pendingDeliveries;
	}

	public Map<AID, EUID> getPendingDeliveries() {
		return pendingDeliveries;
	}

	public boolean isPendingDelivery(AID aid) {
		return pendingDeliveries.containsKey(aid);
	}

	@Override
	public String toString() {
		return "AtomDeliveryState{" +
			"pendingDeliveries=" + pendingDeliveries +
			'}';
	}

	public static AtomDeliveryState empty() {
		return new AtomDeliveryState(ImmutableMap.of());
	}

	@Override
	public Object getDebugRepresentation() {
		return ImmutableMap.of(
			"pendingDeliveries", pendingDeliveries
		);
	}
}
