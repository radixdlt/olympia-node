package com.radixdlt.atomos;

import com.radixdlt.engine.RadixEngineAtom;

/**
 * Exposes the interface which low-level atom output constraints can be built on top of.
 */
public interface AtomOSKernel<T extends RadixEngineAtom> {
	/**
	 * Function which returns result of a kernel check
	 */
	interface AtomKernelConstraintCheck<T extends RadixEngineAtom> {

		/**
		 * Returns whether cmAtom passes this verification check.
		 * @param radixEngineAtom atom to validate
		 * @return success or failure of verification
		 */
		Result check(T radixEngineAtom);
	}

	/**
	 * Computes a value for a given atom
	 */
	interface AtomKernelCompute<T extends RadixEngineAtom> {
		Object compute(T atom);
	}

	/**
	 * Callback interface for creating verification requirements and computation.
	 */
	interface AtomKernel<T extends RadixEngineAtom> {
		void require(AtomKernelConstraintCheck<T> constraint);
		void setCompute(AtomKernelCompute<T> compute);
	}

	/**
	 * System call endpoint which allows an atom model application to program constraints
	 * against an Atom and low-level information.
	 *
	 * This endpoint returns a callback on which the application must define the constraint.
	 * This function MUST be a pure function (i.e. no states)
	 *
	 * @return a callback function onto which the constraint will be defined
	 */
	AtomKernel<T> onAtom();
}
