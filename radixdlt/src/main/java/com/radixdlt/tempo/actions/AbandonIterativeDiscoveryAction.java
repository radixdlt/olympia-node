package com.radixdlt.tempo.actions;

import com.radixdlt.common.EUID;
import com.radixdlt.tempo.reactive.TempoAction;

public class AbandonIterativeDiscoveryAction implements TempoAction {
	private final EUID peerNid;

	public AbandonIterativeDiscoveryAction(EUID peerNid) {
		this.peerNid = peerNid;
	}

	public EUID getPeerNid() {
		return peerNid;
	}
}
