/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

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
	 * Hashes the specified portion of the array, returning a cryptographically secure 256-bit hash
	 *
	 * @param data The data to hash
	 * @param offset The offset within the array to start hashing data
	 * @param length The number of bytes in the array to hash
	 * @return The digest by applying the 256-bit/32-byte hash
	 */
	byte[] hash256(byte[] data, int offset, int length);

	/**
	 * Hashes the specified portion of the array, returning a cryptographically secure 512-bit hash.
	 *
	 * @param data The data to hash
	 * @param offset The offset within the array to start hashing data
	 * @param length The number of bytes in the array to hash
	 * @return The 512-bit/64-byte hash
	 */
	byte[] hash512(byte[] data, int offset, int length);

	/**
	 * Hashes the supplied array, returning a cryptographically secure 256-bit hash
	 *
	 * @param data The data to hash
	 * @return The digest by applying the 256-bit/32-byte hash
	 */
	default byte[] hash256(byte[] data) {
		return hash256(data, 0, data.length);
	}

	/**
	 * Hashes the specified portion of the array, returning a cryptographically secure 512-bit hash.
	 *
	 * @param data The data to hash
	 * @return The 512-bit/64-byte hash
	 */
	default byte[] hash512(byte[] data) {
		return hash512(data, 0, data.length);
	}

}
