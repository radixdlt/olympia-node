package com.radixdlt.tempo.actions;

import com.radixdlt.common.AID;
import com.radixdlt.tempo.TempoAction;
import org.radix.network.peers.Peer;

import java.util.Objects;
import java.util.Set;

public class OnSampleDeliveryFailedAction implements TempoAction {
	private final Set<AID> aids;
	private final Peer peer;

	public OnSampleDeliveryFailedAction(Set<AID> aids, Peer peer) {
		this.aids = Objects.requireNonNull(aids, "aids is required");
		this.peer = Objects.requireNonNull(peer, "peer is required");
	}

	public Set<AID> getAids() {
		return aids;
	}

	public Peer getPeer() {
		return peer;
	}
}
