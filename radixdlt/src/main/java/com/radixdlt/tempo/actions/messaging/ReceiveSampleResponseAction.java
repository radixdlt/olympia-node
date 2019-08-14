package com.radixdlt.tempo.actions.messaging;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.messages.SampleResponseMessage;
import org.radix.network.peers.Peer;
import org.radix.time.TemporalProof;

public class ReceiveSampleResponseAction implements TempoAction {
	private final ImmutableSet<TemporalProof> temporalProofs;
	private final ImmutableSet<AID> missingAids;
	private final Peer peer;

	public ReceiveSampleResponseAction(ImmutableSet<TemporalProof> temporalProofs, ImmutableSet<AID> missingAids, Peer peer) {
		this.temporalProofs = temporalProofs;
		this.missingAids = missingAids;
		this.peer = peer;
	}

	public ImmutableSet<TemporalProof> getTemporalProofs() {
		return temporalProofs;
	}

	public ImmutableSet<AID> getMissingAids() {
		return missingAids;
	}

	public Peer getPeer() {
		return peer;
	}

	public static ReceiveSampleResponseAction from(SampleResponseMessage message, Peer peer) {
		return new ReceiveSampleResponseAction(message.getTemporalProofs(), message.getMissingAids(), peer);
	}
}
