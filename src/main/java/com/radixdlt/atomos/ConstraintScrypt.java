package com.radixdlt.atomos;

/**
 * Radix-style smart-contract-like entrypoint for application constraints.
 */
public interface ConstraintScrypt {

	/**
	 * Entrypoint to the constraint scrypt's logic. With the given
	 * AtomOS interface the smart constraint adds custom
	 * application constraints on top of the ledger
	 *
	 * @param os interface to the system calls to configure constraints on the ledger
	 */
	void main(SysCalls os);
}
