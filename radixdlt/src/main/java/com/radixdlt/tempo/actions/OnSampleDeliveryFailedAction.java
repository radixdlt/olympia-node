package com.radixdlt.tempo.actions;

import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.reactive.TempoAction;
import org.radix.network.peers.Peer;

import java.util.Objects;
import java.util.Set;

public class OnSampleDeliveryFailedAction implements TempoAction {
	private final Set<AID> aids;
	private final EUID tag;
	private final Peer peer;

	public OnSampleDeliveryFailedAction(Set<AID> aids, EUID tag, Peer peer) {
		this.aids = Objects.requireNonNull(aids, "aids is required");
		this.tag = Objects.requireNonNull(tag, "tag is required");
		this.peer = Objects.requireNonNull(peer, "peer is required");
	}

	public Set<AID> getAids() {
		return aids;
	}

	public EUID getTag() {
		return tag;
	}

	public Peer getPeer() {
		return peer;
	}
}
