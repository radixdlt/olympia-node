package com.radixdlt.consensus;

public interface Pacemaker {
	void processTimeout();
	void processedAtom();
}
