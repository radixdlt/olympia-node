package com.radixdlt.engine;

import com.radixdlt.constraintmachine.CMInstruction;

public interface RadixEngineAtom {
	CMInstruction getCMInstruction();
}
