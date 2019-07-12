package com.radixdlt.constraintmachine;

import com.radixdlt.atoms.DataPointer;
import java.util.Set;

/**
 * Utility methods for constructing constraint machine errors
 */
public final class CMErrors {
	private CMErrors() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static CMError fromProcedureError(ProcedureError error, int particleGroupIndex) {
		Set<Long> particleIndices = error.getParticleIndicies();
		final DataPointer dataPointer = particleIndices.isEmpty() || particleIndices.size() > 1
			? DataPointer.ofParticleGroup(particleGroupIndex)
			: DataPointer.ofParticle(particleGroupIndex, particleIndices.iterator().next().intValue());

		return new CMError(dataPointer, CMErrorCode.PROCEDURE_ERROR, error.getErrMsg());
	}

	public static CMError fromKernelProcedureError(KernelProcedureError error) {
		return new CMError(DataPointer.ofAtom(), CMErrorCode.KERNEL_ERROR, error.getErrMsg());
	}
}
