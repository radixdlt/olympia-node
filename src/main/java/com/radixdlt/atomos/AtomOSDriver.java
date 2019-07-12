package com.radixdlt.atomos;

/**
 * Radix-style smart-contract-like entrypoint for kernel constraints.
 */
public interface AtomOSDriver {
	/**
	 * Entrypoint to the constraint scrypt's logic. With the given
	 * AtomDriver interface the constraint scrypts adds custom
	 * kernel constraints on top of the ledger
	 *
	 * @param kernel interface to the kernel to configure constraints on the ledger
	 */
	void main(AtomOSKernel kernel);
}
