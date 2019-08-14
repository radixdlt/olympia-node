package com.radixdlt.tempo.actions;

import com.radixdlt.common.AID;
import com.radixdlt.tempo.TempoAction;
import org.radix.network.peers.Peer;

import java.util.Collection;
import java.util.Objects;

public class TimeoutSampleRequestAction implements TempoAction {
	private final Collection<AID> aids;
	private final Peer peer;

	public TimeoutSampleRequestAction(Collection<AID> aids, Peer peer) {
		this.aids = Objects.requireNonNull(aids, "aids is required");
		this.peer = Objects.requireNonNull(peer, "peer is required");
	}

	public Collection<AID> getAids() {
		return aids;
	}

	public Peer getPeer() {
		return peer;
	}
}
