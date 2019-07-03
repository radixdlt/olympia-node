package com.radixdlt.atomos;

import com.radixdlt.atoms.Atom;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.universe.Universe;

/**
 * Exposes the interface which low-level atom output constraints can be built on top of.
 */
public interface AtomOSKernel {
	/**
	 * Function which returns result of a kernel check
	 */
	interface AtomKernelConstraintCheck {

		/**
		 * Returns whether cmAtom passes this verification check.
		 * @param cmAtom atom to validate
		 * @return success or failure of verification
		 */
		Result check(CMAtom cmAtom);
	}

	/**
	 * Computes a value for a given atom
	 */
	interface AtomKernelCompute {
		Object compute(Atom atom);
	}

	/**
	 * Callback interface for creating verification requirements and computation.
	 */
	interface AtomKernel {
		void require(AtomKernelConstraintCheck constraint);
		void compute(String key, AtomKernelCompute compute);
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
	AtomKernel onAtom();

	/**
	 * Get the current timestamp within the Universe
	 * @return the current timestamp in milliseconds
	 */
	long getCurrentTimestamp();

	/**
	 * Gets the universe of the AtomOS
	 * @return the universe of the AtomOS
	 */
	Universe getUniverse();
}
