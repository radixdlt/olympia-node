package com.radixdlt.atomos;

import com.radixdlt.engine.RadixEngineAtom;
import java.util.stream.Stream;

/**
 * Low-level constraint procedure which has access to the full atom and
 * the result of particle spin processing
 */
public interface KernelConstraintProcedure {
	Stream<KernelProcedureError> validate(RadixEngineAtom radixEngineAtom);
}
