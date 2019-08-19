package com.radixdlt.tempo.actions.messaging;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.messages.PositionDiscoveryRequestMessage;
import org.radix.network.messaging.Message;
import org.radix.network.peers.Peer;

public class SendPositionDiscoveryRequestAction implements TempoAction {
	private final ImmutableSet<Long> positions;
	private final Peer peer;

	public SendPositionDiscoveryRequestAction(ImmutableSet<Long> positions, Peer peer) {
		this.positions = positions;
		this.peer = peer;
	}

	public Peer getPeer() {
		return peer;
	}

	public ImmutableSet<Long> getPositions() {
		return positions;
	}

	public Message toMessage() {
		return new PositionDiscoveryRequestMessage(positions);
	}
}
