package com.radixdlt.tempo.actions;

import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAction;

public class AbandonIterativeSyncAction implements TempoAction {
	private final EUID peerNid;

	public AbandonIterativeSyncAction(EUID peerNid) {
		this.peerNid = peerNid;
	}

	public EUID getPeerNid() {
		return peerNid;
	}
}
