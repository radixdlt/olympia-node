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
import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;
import com.radixdlt.crypto.ECDSASignature;

/**
 * Signs a hash.
 */
@FunctionalInterface
@SecurityCritical(SecurityKind.SIG_SIGN)
public interface HashSigner {
	/**
	 * Sign the specified hash with the specified key.
	 *
	 * @param hash The hash to sign
	 * @return The {@link ECDSASignature}
	 */
	ECDSASignature sign(byte[] hash);

	/**
	 * Sign the specified hash with the specified key.
	 *
	 * @param hash The hash to sign
	 * @return The {@link ECDSASignature}
	 */
	default ECDSASignature sign(HashCode hash) {
		return sign(hash.asBytes());
	}
}
