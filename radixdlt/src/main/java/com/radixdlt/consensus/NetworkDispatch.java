package com.radixdlt.consensus;

import com.radixdlt.common.Atom;

public interface NetworkDispatch {
	void broadcastProposal(Atom atom);
}
