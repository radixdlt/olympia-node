package com.radixdlt.compute;

import com.radixdlt.constraintmachine.CMAtom;

/**
 * Computation (rather than validation) done per atom.
 */
public interface AtomCompute {
	Object compute(CMAtom cmAtom);
}
