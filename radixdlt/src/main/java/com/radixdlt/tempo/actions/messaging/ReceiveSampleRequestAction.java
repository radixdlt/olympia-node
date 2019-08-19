package com.radixdlt.tempo.actions.messaging;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.messages.SampleRequestMessage;
import org.radix.network.peers.Peer;

public class ReceiveSampleRequestAction implements TempoAction {
	private final ImmutableSet<AID> aids;
	private final Peer peer;
	private final EUID tag;

	public ReceiveSampleRequestAction(ImmutableSet<AID> aids, EUID tag, Peer peer) {
		this.aids = aids;
		this.peer = peer;
		this.tag = tag;
	}

	public ImmutableSet<AID> getAids() {
		return aids;
	}

	public EUID getTag() {
		return tag;
	}

	public Peer getPeer() {
		return peer;
	}

	public static ReceiveSampleRequestAction from(SampleRequestMessage message, Peer peer) {
		return new ReceiveSampleRequestAction(message.getAids(), message.getTag(), peer);
	}
}
