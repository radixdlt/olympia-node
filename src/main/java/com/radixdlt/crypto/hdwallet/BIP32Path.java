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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;

import java.util.ArrayList;
import java.util.Arrays;

final class BIP32Path implements HDPath {


	/**
	 * "m/" marks inclusion of private key, whereas "M/" (capital M) marks public key only.
	 */
	public static final char BIP32_PRIVATE_KEY_BIP44_PREFIX_CHAR = 'm';
	public static final char BIP32_PUBLIC_KEY_ONLY_BIP44_PREFIX_CHAR = 'M';


	public static final char BIP32_PATH_DELIMITER = '/';
	public static final String BIP32_PRIVATE_KEY_BIP44_PREFIX = BIP32_PRIVATE_KEY_BIP44_PREFIX_CHAR + String.valueOf(BIP32_PATH_DELIMITER);
	public static final char BIP32_HARDENED_PATH_STANDARD_MARKER = '\'';

	public static final String BIP39_MNEMONIC_NO_PASSPHRASE = "";

	private static final long hardenedOffset = 2147483648L;

	private final String path;
	private final boolean isHardened;
	private final int depth;
	private final long index;

	BIP32Path(String path) throws HDPathException {

		path = path.replace(" ", "");

		CharMatcher matcher = CharMatcher.inRange('0', '9')
				.or(CharMatcher.is(BIP32_PRIVATE_KEY_BIP44_PREFIX_CHAR))
				.or(CharMatcher.is(BIP32_PUBLIC_KEY_ONLY_BIP44_PREFIX_CHAR))
				.or(CharMatcher.is(BIP32_PATH_DELIMITER))
				;

		if (!matcher.matchesAnyOf(path)) {
			throw HDPathException.invalidString;
		}

		this.path = path;
		String[] pathComponents = path.split(String.valueOf(BIP32_PATH_DELIMITER));
		this.depth = pathComponents.length - 1;

		this.isHardened = path.endsWith(String.valueOf(BIP32_HARDENED_PATH_STANDARD_MARKER));
		String lastComponent = pathComponents[depth];
		if (isHardened) {
			lastComponent = lastComponent.replace(String.valueOf(BIP32_HARDENED_PATH_STANDARD_MARKER), "");
		}
		long indexNonHardened = Long.parseUnsignedLong(lastComponent);
		this.index = isHardened ? (indexNonHardened + hardenedOffset) : indexNonHardened;
	}

	public String toString() {
		return path;
	}

	public boolean isHardened() {
		return isHardened;
	}

	public int depth() {
		return depth;
	}

	public long index() {
		return index;
	}

	public HDPath next() {
		try {
			return new BIP32Path(pathOfNextKey());
		} catch (HDPathException e) {
			throw new IllegalArgumentException("Failed to construct next HDPath " + e);
		}
	}

	private String pathOfNextKey() {
		return pathOfKeySubsequentToPath(this);
	}

	/** Returns the `index` without the hardening bit set  */
	private int num() {
		if (!isHardened) {
			return (int) index;
		}
		return (int) (index - hardenedOffset);
	}

	@VisibleForTesting
	static String pathOfKeySubsequentToPath(BIP32Path path) {
		int currentIndex = path.num();
		if (currentIndex == Integer.MAX_VALUE) {
			throw new IllegalStateException("Already at max index");
		}

		ArrayList<String> pathComponents = new ArrayList<>(Arrays.asList(path.path.split(String.valueOf(BIP32_PATH_DELIMITER))).subList(0, path.depth));
		pathComponents.add(String.format("%d%s", currentIndex + 1, path.isHardened ? BIP32_HARDENED_PATH_STANDARD_MARKER : ""));
		return String.join(String.valueOf(BIP32_PATH_DELIMITER), pathComponents);
	}

	@VisibleForTesting
	static String pathOfKeySubsequentToPath(String path) {
		try {
			BIP32Path bip32Path = new BIP32Path(path);
			return pathOfKeySubsequentToPath(bip32Path);
		} catch (HDPathException e) {
			throw new IllegalArgumentException("Failed to construct HDPath " + e);
		}
	}
}
