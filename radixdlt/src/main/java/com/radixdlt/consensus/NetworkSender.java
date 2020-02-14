package com.radixdlt.consensus;

import com.radixdlt.common.Atom;

public interface NetworkSender {
	void broadcastProposal(Atom atom);
}
