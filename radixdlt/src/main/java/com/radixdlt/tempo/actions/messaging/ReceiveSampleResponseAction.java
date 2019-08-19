package com.radixdlt.tempo.actions.messaging;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.messages.SampleResponseMessage;
import org.radix.network.peers.Peer;
import org.radix.time.TemporalProof;

public class ReceiveSampleResponseAction implements TempoAction {
	private final ImmutableSet<TemporalProof> temporalProofs;
	private final ImmutableSet<AID> unavailableAids;
	private final EUID tag;
	private final Peer peer;

	public ReceiveSampleResponseAction(ImmutableSet<TemporalProof> temporalProofs, ImmutableSet<AID> unavailableAids, EUID tag, Peer peer) {
		this.temporalProofs = temporalProofs;
		this.unavailableAids = unavailableAids;
		this.tag = tag;
		this.peer = peer;
	}

	public ImmutableSet<TemporalProof> getTemporalProofs() {
		return temporalProofs;
	}

	public ImmutableSet<AID> getUnavailableAids() {
		return unavailableAids;
	}

	public EUID getTag() {
		return tag;
	}

	public Peer getPeer() {
		return peer;
	}

	public static ReceiveSampleResponseAction from(SampleResponseMessage message, Peer peer) {
		return new ReceiveSampleResponseAction(message.getTemporalProofs(), message.getUnavailableAids(), message.getTag(), peer);
	}
}
