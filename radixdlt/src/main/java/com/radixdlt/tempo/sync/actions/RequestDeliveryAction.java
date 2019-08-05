package com.radixdlt.tempo.sync.actions;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.tempo.sync.SyncAction;
import org.radix.network.peers.Peer;

public class RequestDeliveryAction implements SyncAction {
	private final ImmutableList<AID> aids;
	private final Peer peer;

	public RequestDeliveryAction(ImmutableList<AID> aids, Peer peer) {
		this.aids = aids;
		this.peer = peer;
	}

	public ImmutableList<AID> getAids() {
		return aids;
	}

	public Peer getPeer() {
		return peer;
	}
}
