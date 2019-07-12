package com.radixdlt.crypto;

/**
 * Interface for signature and public key computation functions.
 * <p>
 * The intent behind this interface is that the actual implementations can
 * easily be replaced when required.
 * <p>
 * Note that all methods must be thread safe.
 */
interface KeyHandler {
	/**
	 * Sign the specified hash with the specified private key.
	 *
	 * @param hash The hash to sign
	 * @param privateKey The private key to sign the hash with
	 * @return An {@link ECSignature} with {@code r} and {@code s} values included
	 * @throws CryptoException if the {@code privateKey} is invalid
	 */
	ECSignature sign(byte[] hash, byte[] privateKey) throws CryptoException;

	/**
	 * Verify the specified signature against the specified hash with the
	 * specified public key.
	 *
	 * @param hash The hash to verify against
	 * @param signature The signature to verify
	 * @param publicKey The public key to verify the signature with
	 * @return An boolean indicating whether the signature could be successfully validated
	 * @throws CryptoException if the {@code publicKey} or {@code signature} is invalid
	 */
	boolean verify(byte[] hash, ECSignature signature, byte[] publicKey) throws CryptoException;

	/**
	 * Compute a public key for the specified private key.
	 *
	 * @param privateKey The private key to compute the public key for
	 * @return A compressed public key
	 * @throws CryptoException If the {@code privateKey} is invalid
	 */
	byte[] computePublicKey(byte[] privateKey) throws CryptoException;
}
