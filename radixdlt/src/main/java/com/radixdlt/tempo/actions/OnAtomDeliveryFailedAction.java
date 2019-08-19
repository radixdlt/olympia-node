package com.radixdlt.tempo.actions;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.tempo.reactive.TempoAction;
import org.radix.network.peers.Peer;

import java.util.Objects;

public class OnAtomDeliveryFailedAction implements TempoAction {
	private final ImmutableList<AID> aids;
	private final Peer peer;

	public OnAtomDeliveryFailedAction(ImmutableList<AID> aids, Peer peer) {
		this.aids = Objects.requireNonNull(aids, "aids is required");
		this.peer = Objects.requireNonNull(peer, "peer is required");
	}

	public ImmutableList<AID> getAids() {
		return aids;
	}

	public Peer getPeer() {
		return peer;
	}
}
