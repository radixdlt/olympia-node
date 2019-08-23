package com.radixdlt.compute;

import com.radixdlt.engine.RadixEngineAtom;

/**
 * Computation (rather than validation) done per atom.
 */
public interface AtomCompute<T extends RadixEngineAtom> {
	Object compute(T cmAtom);
}
