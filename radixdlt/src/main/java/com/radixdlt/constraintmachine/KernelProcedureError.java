package com.radixdlt.constraintmachine;

import java.util.Objects;
import org.radix.atoms.Atom;

/**
 * An error from a kernel constraint procedure
 */
public final class KernelProcedureError {
	private final Atom atom;
	private final String errMsg;

	KernelProcedureError(Atom atom, String errMsg) {
		this.atom = Objects.requireNonNull(atom);
		this.errMsg = Objects.requireNonNull(errMsg);
	}

	public static KernelProcedureError of(Atom atom, String errMsg) {
		return new KernelProcedureError(atom, errMsg);
	}

	public Atom getAtom() {
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
