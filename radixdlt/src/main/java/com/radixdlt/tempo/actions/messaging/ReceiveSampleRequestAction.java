package com.radixdlt.tempo.actions.messaging;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.messages.SampleRequestMessage;
import org.radix.network.peers.Peer;

public class ReceiveSampleRequestAction implements TempoAction {
	private final ImmutableSet<AID> aids;
	private final Peer peer;

	public ReceiveSampleRequestAction(ImmutableSet<AID> aids, Peer peer) {
		this.aids = aids;
		this.peer = peer;
	}

	public ImmutableSet<AID> getAids() {
		return aids;
	}

	public Peer getPeer() {
		return peer;
	}

	public static ReceiveSampleRequestAction from(SampleRequestMessage message, Peer peer) {
		return new ReceiveSampleRequestAction(message.getAids(), peer);
	}
}
