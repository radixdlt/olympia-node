package com.radixdlt.engine;

import com.radixdlt.constraintmachine.CMInstruction;

public interface CMAtom {
	CMInstruction getCMInstruction();
}
