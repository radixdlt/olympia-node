/*
 *
 *  * (C) Copyright 2020 Radix DLT Ltd
 *  *
 *  * Radix DLT Ltd licenses this file to you under the Apache License,
 *  * Version 2.0 (the "License"); you may not use this file except in
 *  * compliance with the License.  You may obtain a copy of the
 *  * License at
 *  *
 *  *  http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing,
 *  * software distributed under the License is distributed on an
 *  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  * either express or implied.  See the License for the specific
 *  * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.crypto.hdwallet;

import com.google.common.collect.ImmutableList;
import org.apache.logging.log4j.core.util.Integers;

public interface HDPath {

	/**
	 * The string representation of the BIP32 path, using standard notation "'" for hardened components, e.g.
	 * "m/44'/536'/2'/1/4
	 * @return a string representation of the BIP32 path, using standard notation "'" for hardened components, e.g.
	 * 	 * "m/44'/536'/2'/1/4
	 */
	String toString();

	/**
	 * Is this a path to a private key?
	 *
	 * @return true if yes, false if no or a partial path
	 */
	boolean hasPrivateKey();

	/**
	 * Whether the last component in the path is "hardened" or not, if the last component is not hardened, it does not mean
	 * that potentially earlier components are not hardened as well, i.e. this only looks at the <b>last</b> component.
	 * @return whether the last component in the path is "hardened" or not, if the last component is not hardened, it does not mean
	 * 	 * that potentially earlier components are not hardened as well, i.e. this only looks at the <b>last</b> component.
	 */
	boolean isHardened();

	/**
	 * The number of components in the path, `1` is the lowest possible value, and most commonly 5 is the max depth, even though BIP32
	 * supports a longer depth. The depth of "m/0" is 1, the depth of "m/0'/1" is 2 etc.
	 * @return number of components in the path, `1` is the lowest possible value, and most commonly 5 is the max depth, even though BIP32
	 * 	 * supports a longer depth. The depth of "m/0" is 1, the depth of "m/0'/1" is 2 etc.
	 */
	int depth();

	/**
	 * Returns the value of the last component, taking into account if it is hardened or not, i.e. the index of the path "m/0/0" is 0, but
	 * the index of the path "m/0/0'" - which is hardened - is 2147483648 (0 | HARDENED_BITMASK) - and the index of "m/0/1'" is 2147483649
	 * (1 | HARDENED_BITMASK).
	 * @return the value of the last component, taking into account if it is hardened or not, i.e. the index of the path "m/0/0" is 0, but
	 * 	 * the index of the path "m/0/0'" - which is hardened - is 2147483648 (0 | HARDENED_BITMASK) - and the index of "m/0/1'" is 2147483649
	 * 	 * (1 | HARDENED_BITMASK).
	 */
	long index();

	HDPath next();

	String BIP39_MNEMONIC_NO_PASSPHRASE = "";
	String BIP32_HARDENED_MARKER_STANDARD = "'";

	String BIP32_PATH_SEPARATOR = "/";
	String BIP32_PREFIX_PRIVATEKEY = "m";


	static boolean validateBIP32Path(String path) {
		return validateBIP32Path(path, BIP32_HARDENED_MARKER_STANDARD);
	}

	static boolean validateBIP32Path(String path, String hardenedMarker) {
		// Check trivial paths
		if (ImmutableList.of("", BIP32_PREFIX_PRIVATEKEY, BIP32_PATH_SEPARATOR).contains(path)) {
			return true;
		}
		if (path.startsWith("M/") || path.startsWith("m/")) {
			path = path.substring(2);
		}

		if (path.isEmpty()) {
			return false;
		}

		if (path.contains("//")) {
			return false;
		}

		for (String component : path.split(BIP32_PATH_SEPARATOR)) {
			if (component.isEmpty()) {
				return false;
			}
			if (component.endsWith(hardenedMarker)) {
				component = component.replace(hardenedMarker, "");
			}
			boolean isNumber;
			try {
				Integers.parseInt(component);
				isNumber = true;
			} catch (Exception e) {
				isNumber = false;
			}
			if (!isNumber) {
				return false;
			}
		}

		return true;
	}
}
