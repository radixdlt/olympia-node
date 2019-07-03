package com.radixdlt.crypto;

/**
 * Interface for abstract 256-bit and 512-bit cryptographic hash functions.
 * <p>
 * The intent behind this interface is that the actual hash functions can
 * easily be replaced when required.
 * <p>
 * Note that all methods must be thread safe.
 */
interface HashHandler {

	/**
	 * Hashes the supplied array, returning a cryptographically secure 256-bit hash.
	 *
	 * @param data The data to hash
	 * @return The 256-bit/32-byte hash
	 */
	byte[] hash256(byte[] data);

	/**
	 * Hashes the specified portion of the array, returning a cryptographically secure 256-bit hash.
	 *
	 * @param data The data to hash
	 * @param offset The offset within the array to start hashing data
	 * @param length The number of bytes in the array to hash
	 * @return The 256-bit/32-byte hash
	 */
	byte[] hash256(byte[] data, int offset, int length);

	/**
	 * Hashes the supplied arrays, returning a cryptographically secure 256-bit hash.
	 * The hash is calculated as if the arrays were concatenated into a single array.
	 *
	 * @param data0 The first part of the data to hash
	 * @param data1 The second part of the data to hash
	 * @return The 256-bit/32-byte hash
	 */
	byte[] hash256(byte[] data0, byte[] data1);

	/**
	 * Hashes the specified portion of the array, returning a cryptographically secure 512-bit hash.
	 *
	 * @param data The data to hash
	 * @param offset The offset within the array to start hashing data
	 * @param length The number of bytes in the array to hash
	 * @return The 512-bit/64-byte hash
	 */
	byte[] hash512(byte[] data, int offset, int length);

}
