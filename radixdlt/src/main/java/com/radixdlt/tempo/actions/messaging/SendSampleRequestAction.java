package com.radixdlt.tempo.actions.messaging;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.messages.SampleRequestMessage;
import org.radix.network.peers.Peer;

public class SendSampleRequestAction implements TempoAction {
	private final ImmutableSet<AID> aids;
	private final Peer peer;
	private final EUID tag;

	public SendSampleRequestAction(ImmutableSet<AID> aids, Peer peer, EUID tag) {
		this.aids = aids;
		this.peer = peer;
		this.tag = tag;
	}

	public ImmutableSet<AID> getAids() {
		return aids;
	}

	public Peer getPeer() {
		return peer;
	}

	public SampleRequestMessage toMessage() {
		return new SampleRequestMessage(aids, tag);
	}
}
