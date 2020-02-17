package com.radixdlt.consensus;

import com.radixdlt.common.EUID;

public class DumbProposerElection implements ProposerElection {
	@Override
	public boolean isValidProposer(EUID nid, long round) {
		// will accept anything
		return true;
	}
}
