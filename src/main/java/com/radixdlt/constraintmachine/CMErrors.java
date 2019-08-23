package com.radixdlt.constraintmachine;

import com.radixdlt.atomos.KernelProcedureError;
import com.radixdlt.atoms.DataPointer;

/**
 * Utility methods for constructing constraint machine errors
 */
public final class CMErrors {
	private CMErrors() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static CMError fromKernelProcedureError(KernelProcedureError error) {
		return new CMError(DataPointer.ofAtom(), CMErrorCode.KERNEL_ERROR, null, error.getErrMsg());
	}
}
