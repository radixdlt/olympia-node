package com.radixdlt.tempo.actions.messaging;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.AID;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.messages.PositionDiscoveryResponseMessage;
import org.radix.network.peers.Peer;

public class ReceivePositionDiscoveryResponseAction implements TempoAction {
	private final ImmutableMap<Long, AID> aids;
	private final Peer peer;

	public ReceivePositionDiscoveryResponseAction(ImmutableMap<Long, AID> aids, Peer peer) {
		this.aids = aids;
		this.peer = peer;
	}

	public ImmutableMap<Long, AID> getAids() {
		return aids;
	}

	public Peer getPeer() {
		return peer;
	}

	public static ReceivePositionDiscoveryResponseAction from(PositionDiscoveryResponseMessage message, Peer peer) {
		return new ReceivePositionDiscoveryResponseAction(message.getAids(), peer);
	}
}
