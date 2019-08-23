package com.radixdlt.compute;

import com.radixdlt.engine.CMAtom;

/**
 * Computation (rather than validation) done per atom.
 */
public interface AtomCompute<T extends CMAtom> {
	Object compute(T cmAtom);
}
