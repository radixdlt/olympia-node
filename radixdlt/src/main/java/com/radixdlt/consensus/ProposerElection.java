package com.radixdlt.consensus;

import com.radixdlt.common.EUID;

public interface ProposerElection {
	boolean isValidProposer(EUID nid, long round);
}
