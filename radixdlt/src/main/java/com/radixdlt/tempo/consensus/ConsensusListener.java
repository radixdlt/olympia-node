package com.radixdlt.tempo.consensus;

// TODO unsure about interface here, mainly for testing
public interface ConsensusListener {
	void accept(ConsensusAction action);
}
