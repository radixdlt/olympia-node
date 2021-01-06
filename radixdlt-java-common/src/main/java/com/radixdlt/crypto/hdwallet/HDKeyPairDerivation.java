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

package com.radixdlt.crypto.hdwallet;

/**
 * A type being able to derive hierarchy deterministic key pairs ({@link HDKeyPair}),
 * from a {@link HDPath}, typically using
 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki">BIP32 (BIP-32)</a>
 * child key derivation.
 */
public interface HDKeyPairDerivation {

	/**
	 * Derives an {@link HDKeyPair} for the given {@link HDPath}
	 * @param path used to derive {@link HDKeyPair}
	 * @return a derived {@link HDKeyPair} for the given {@link HDPath}
	 */
	HDKeyPair deriveKeyAtPath(HDPath path);

	/**
	 * Tries to derives an {@link HDKeyPair} for the given {@code path} string, if the string is invalid,
	 * then {@link IllegalArgumentException} will be thrown, so use this method with caution and consider
	 * using {@link #deriveKeyAtPath(HDPath)} instead.
	 * @param path used to derive {@link HDKeyPair}
	 * @return a derived {@link HDKeyPair} for the given {@code path} string, if the string is valid path, else
	 *  an {@link IllegalArgumentException} will be thrown.
	 */
	default HDKeyPair deriveKeyAtPath(String path) {
		try {
			HDPath hdPath = DefaultHDPath.of(path);
			return deriveKeyAtPath(hdPath);
		} catch (HDPathException e) {
			throw new IllegalArgumentException("Failed to construct HD path " + e);
		}
	}
}
