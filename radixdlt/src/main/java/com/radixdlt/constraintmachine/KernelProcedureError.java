package com.radixdlt.constraintmachine;

import com.radixdlt.atoms.ImmutableAtom;
import java.util.Objects;

/**
 * An error from a kernel constraint procedure
 */
public final class KernelProcedureError {
	private final ImmutableAtom atom;
	private final String errMsg;

	KernelProcedureError(ImmutableAtom atom, String errMsg) {
		this.atom = Objects.requireNonNull(atom);
		this.errMsg = Objects.requireNonNull(errMsg);
	}

	public static KernelProcedureError of(ImmutableAtom atom, String errMsg) {
		return new KernelProcedureError(atom, errMsg);
	}

	public ImmutableAtom getAtom() {
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
