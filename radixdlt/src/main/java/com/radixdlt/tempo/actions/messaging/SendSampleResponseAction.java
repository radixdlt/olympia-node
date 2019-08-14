package com.radixdlt.tempo.actions.messaging;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.messages.SampleResponseMessage;
import org.radix.network.peers.Peer;
import org.radix.time.TemporalProof;

public class SendSampleResponseAction implements TempoAction {
	private final ImmutableSet<TemporalProof> temporalProofs;
	private final ImmutableSet<AID> missingAids;
	private final Peer peer;

	public SendSampleResponseAction(ImmutableSet<TemporalProof> temporalProofs, ImmutableSet<AID> missingAids, Peer peer) {
		this.temporalProofs = temporalProofs;
		this.peer = peer;
		this.missingAids = missingAids;
	}

	public Peer getPeer() {
		return peer;
	}

	public SampleResponseMessage toMessage() {
		return new SampleResponseMessage(temporalProofs, missingAids);
	}
}
