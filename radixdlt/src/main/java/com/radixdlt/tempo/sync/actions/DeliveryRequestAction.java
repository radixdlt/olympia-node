package com.radixdlt.tempo.sync.actions;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.messages.DeliveryRequestMessage;
import org.radix.network.peers.Peer;

public class DeliveryRequestAction implements SyncAction {
	private final ImmutableList<AID> aids;
	private final Peer peer;

	public DeliveryRequestAction(ImmutableList<AID> aids, Peer peer) {
		this.aids = aids;
		this.peer = peer;
	}

	public ImmutableList<AID> getAids() {
		return aids;
	}

	public Peer getPeer() {
		return peer;
	}

	public DeliveryRequestMessage toMessage() {
		return new DeliveryRequestMessage(aids);
	}

	public static DeliveryRequestAction from(DeliveryRequestMessage message, Peer peer) {
		return new DeliveryRequestAction(ImmutableList.copyOf(message.getAids()), peer);
	}
}
