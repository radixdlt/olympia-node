package com.radixdlt.tempo.actions;

import com.radixdlt.common.EUID;
import com.radixdlt.tempo.reactive.TempoAction;

public class OnDiscoveryCursorSynchronisedAction implements TempoAction {
	private final EUID peerNid;

	public OnDiscoveryCursorSynchronisedAction(EUID peerNid) {
		this.peerNid = peerNid;
	}

	public EUID getPeerNid() {
		return peerNid;
	}
}
