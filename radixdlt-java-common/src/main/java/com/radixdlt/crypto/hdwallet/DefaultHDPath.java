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

public final class DefaultHDPath {
	private DefaultHDPath() {
		throw new IllegalStateException("Can't construct.");
	}

	/**
	 * Tries to create a {@link HDPath} instance from the given {@code path}. If said path is not a valid HDPath,
	 * an exception will be thrown.
	 * @param path a string representing a hierarchy deterministic path, typically
	 *                <a href="https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki">BIP32 (BIP-32)</a>
	 *                compliant.
	 * @return a valid hierarchy deterministic path ({@link HDPath}).
	 * @throws HDPathException thrown if {@code path} is invalid.
	 */
	public static HDPath of(String path) throws HDPathException {
		return BIP32Path.fromString(path);
	}
}
