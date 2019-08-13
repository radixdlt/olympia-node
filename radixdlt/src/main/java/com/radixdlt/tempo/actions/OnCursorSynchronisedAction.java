package com.radixdlt.tempo.actions;

import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAction;

public class OnCursorSynchronisedAction implements TempoAction {
	private final EUID peerNid;

	public OnCursorSynchronisedAction(EUID peerNid) {
		this.peerNid = peerNid;
	}

	public EUID getPeerNid() {
		return peerNid;
	}
}
