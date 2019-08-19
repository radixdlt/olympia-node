package com.radixdlt.tempo.actions.messaging;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.AID;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.messages.PositionDiscoveryRequestMessage;
import com.radixdlt.tempo.messages.PositionDiscoveryResponseMessage;
import org.radix.network.messaging.Message;
import org.radix.network.peers.Peer;

public class SendPositionDiscoveryResponseAction implements TempoAction {
	private final ImmutableMap<Long, AID> aids;
	private final Peer peer;

	public SendPositionDiscoveryResponseAction(ImmutableMap<Long, AID> aids, Peer peer) {
		this.aids = aids;
		this.peer = peer;
	}

	public Peer getPeer() {
		return peer;
	}

	public Message toMessage() {
		return new PositionDiscoveryResponseMessage(aids);
	}
}
