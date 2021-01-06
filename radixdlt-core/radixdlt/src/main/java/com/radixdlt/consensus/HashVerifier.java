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

package com.radixdlt.consensus;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;

/**
 * Verifies signatures against hashes.
 */
@FunctionalInterface
public interface HashVerifier {
	/**
	 * Verify the specified signature against the specified hash with
	 * the specified public key.
	 *
	 * @param pubKey The public key to verify with
	 * @param hash The the hash to verify
	 * @param sig The signature to verify
	 * @return {@code true} if the signature matches, {@code false} otherwise
	 */
	boolean verify(ECPublicKey pubKey, HashCode hash, ECDSASignature sig);
}
