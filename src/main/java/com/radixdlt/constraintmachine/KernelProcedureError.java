package com.radixdlt.constraintmachine;

import com.radixdlt.engine.RadixEngineAtom;
import java.util.Objects;

/**
 * An error from a kernel constraint procedure
 */
public final class KernelProcedureError {
	private final RadixEngineAtom atom;
	private final String errMsg;

	KernelProcedureError(RadixEngineAtom atom, String errMsg) {
		this.atom = Objects.requireNonNull(atom);
		this.errMsg = Objects.requireNonNull(errMsg);
	}

	public static KernelProcedureError of(RadixEngineAtom atom, String errMsg) {
		return new KernelProcedureError(atom, errMsg);
	}

	public RadixEngineAtom getAtom() {
		return atom;
	}

	public String getErrMsg() {
		return errMsg;
	}

	@Override
	public String toString() {
		return errMsg;
	}
}
