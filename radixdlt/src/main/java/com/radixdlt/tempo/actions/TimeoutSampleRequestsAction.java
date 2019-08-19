package com.radixdlt.tempo.actions;

import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAction;
import org.radix.network.peers.Peer;

import java.util.Collection;
import java.util.Objects;

public class TimeoutSampleRequestsAction implements TempoAction {
	private final Collection<AID> aids;
	private final Collection<Peer> peer;
	private final EUID tag;

	public TimeoutSampleRequestsAction(Collection<AID> aids, Collection<Peer> peers, EUID tag) {
		this.aids = Objects.requireNonNull(aids, "aids is required");
		this.peer = Objects.requireNonNull(peers, "peers is required");
		this.tag = Objects.requireNonNull(tag, "tag is required");
	}

	public Collection<AID> getAids() {
		return aids;
	}

	public Collection<Peer> getPeers() {
		return peer;
	}

	public EUID getTag() {
		return tag;
	}
}
