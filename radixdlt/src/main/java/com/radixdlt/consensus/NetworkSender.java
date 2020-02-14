package com.radixdlt.consensus;

import com.radixdlt.common.Atom;

/**
 * Interface for Event Coordinator to send things through a network
 */
public interface NetworkSender {
	void broadcastProposal(Atom atom);
}
