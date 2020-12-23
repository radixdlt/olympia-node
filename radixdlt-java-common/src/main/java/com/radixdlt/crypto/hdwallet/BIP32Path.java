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


import com.google.common.base.Objects;

/**
 * A wrapper around some underlying
 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki">BIP32 (BIP-32)</a> implementation,
 * that is easily swappable.
 *
 * This class doesn't and shouldn't inherit from said wrapped implementation, but rather use
 * it as a trampoline. Since all interfaces are forwarded to the underlying wrapped implementation
 * this class should really be trivial.
 *
 * However, users are discouraged to construct instances of this class directly, they should
 * rather be using {@link DefaultHDPath}.
 */
final class BIP32Path implements HDPath {

	private final HDPath path;

	private BIP32Path(String path) throws HDPathException {
		this.path = BitcoinJBIP32Path.fromString(path);
	}

	static BIP32Path fromString(String path) throws HDPathException {
		return new BIP32Path(path);
	}

	@Override
	public boolean isHardened() {
		return path.isHardened();
	}

	@Override
	public boolean hasPrivateKey() {
		return path.hasPrivateKey();
	}

	@Override
	public String toString() {
		return path.toString();
	}

	@Override
	public int depth() {
		return path.depth();
	}

	@Override
	public long index() {
		return path.index();
	}

	@Override
	public HDPath next() {
		return path.next();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		BIP32Path bip32Path = (BIP32Path) o;
		return Objects.equal(path, bip32Path.path);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(path);
	}
}
