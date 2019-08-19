package com.radixdlt.tempo.actions.messaging;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.messages.SampleResponseMessage;
import org.radix.network.peers.Peer;
import org.radix.time.TemporalProof;

public class SendSampleResponseAction implements TempoAction {
	private final ImmutableSet<TemporalProof> temporalProofs;
	private final ImmutableSet<AID> missingAids;
	private final EUID tag;
	private final Peer peer;

	public SendSampleResponseAction(ImmutableSet<TemporalProof> temporalProofs, ImmutableSet<AID> missingAids, EUID tag, Peer peer) {
		this.temporalProofs = temporalProofs;
		this.tag = tag;
		this.peer = peer;
		this.missingAids = missingAids;
	}

	public Peer getPeer() {
		return peer;
	}

	public SampleResponseMessage toMessage() {
		return new SampleResponseMessage(temporalProofs, missingAids, tag);
	}
}
