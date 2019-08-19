package com.radixdlt.tempo.actions.messaging;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.messages.PositionDiscoveryRequestMessage;
import org.radix.network.messaging.Message;
import org.radix.network.peers.Peer;

public class ReceivePositionDiscoveryRequestAction implements TempoAction {
	private final ImmutableSet<Long> positions;
	private final Peer peer;

	public ReceivePositionDiscoveryRequestAction(ImmutableSet<Long> positions, Peer peer) {
		this.positions = positions;
		this.peer = peer;
	}

	public ImmutableSet<Long> getPositions() {
		return positions;
	}

	public Peer getPeer() {
		return peer;
	}

	public static ReceivePositionDiscoveryRequestAction from(PositionDiscoveryRequestMessage message, Peer peer) {
		return new ReceivePositionDiscoveryRequestAction(message.getPositions(), peer);
	}
}
