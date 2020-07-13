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

public final class HDPaths {

	private HDPaths() {
		throw new IllegalStateException("Can't construct.");
	}

	public static final String BIP39_MNEMONIC_NO_PASSPHRASE = "";
	public static final String BIP32_HARDENED_MARKER_STANDARD = "'";

	public static final String BIP32_PATH_SEPARATOR = "/";
	public static final String BIP32_PREFIX_PRIVATEKEY = "m";

	// The BIP32 bitmask for hardened path components 0x80000000
	public static final long BIP32_HARDENED_VALUE_INCREMENT = 2147483648L;

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
			boolean isValidNumber = false;
			try {
				int number = Integers.parseInt(component);
				if (number >= 0) {
					isValidNumber = true;
				}
			} catch (Exception e) {
				//  Ignored
			}

			if (!isValidNumber) {
				return false;
			}
		}

		return true;
	}
}
