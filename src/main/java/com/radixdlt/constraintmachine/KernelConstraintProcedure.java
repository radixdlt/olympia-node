package com.radixdlt.constraintmachine;

import com.radixdlt.engine.CMAtom;
import java.util.stream.Stream;

/**
 * Low-level constraint procedure which has access to the full atom and
 * the result of particle spin processing
 */
public interface KernelConstraintProcedure {
	Stream<KernelProcedureError> validate(CMAtom cmAtom);
}
