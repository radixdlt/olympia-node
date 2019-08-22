package com.radixdlt.constraintmachine;

import java.util.Objects;

/**
 * An error from a kernel constraint procedure
 */
public final class KernelProcedureError {
	private final CMAtom atom;
	private final String errMsg;

	KernelProcedureError(CMAtom atom, String errMsg) {
		this.atom = Objects.requireNonNull(atom);
		this.errMsg = Objects.requireNonNull(errMsg);
	}

	public static KernelProcedureError of(CMAtom atom, String errMsg) {
		return new KernelProcedureError(atom, errMsg);
	}

	public CMAtom getAtom() {
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
