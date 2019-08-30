package com.radixdlt.tempo.consensus;

public enum AtomConsensusState {
	UNKNOWN,
	NOT_PREFERRED_BUT_NOT_COMMITTED,
	PREFERRED_BUT_NOT_COMMITTED,
	COMMITTED,
	REJECTED_COMMITTED_TO_CONFLICTING_ATOM
}
