package com.radixdlt.consensus;

import com.radixdlt.common.EUID;

/**
 * Represents the election for valid proposers
 */
public interface ProposerElection {
	/**
	 * Check whether a node is a valid proposer in a certain round
	 * @param nid The nid
	 * @param round The round
	 * @return Whether the node is a valid proposer
	 */
	boolean isValidProposer(EUID nid, long round);
}
