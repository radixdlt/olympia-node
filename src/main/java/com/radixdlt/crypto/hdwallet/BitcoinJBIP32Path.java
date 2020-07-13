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

import org.bitcoinj.crypto.ChildNumber;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A BIP32 path wrapping underlying implementation using BitcoinJ.
 */
class BitcoinJBIP32Path implements HDPath {

	private static final String BIP32_HARDENED_MARKER_BITCOINJ = "H";

	private static final long hardenedOffset = 2147483648L;


	private final org.bitcoinj.crypto.HDPath path;

	private BitcoinJBIP32Path(org.bitcoinj.crypto.HDPath path) {
		this.path = path;
	}

	static BitcoinJBIP32Path fromPath(HDPath path) {
		try {
			return fromString(path.toString());
		} catch (HDPathException e) {
			throw new IllegalStateException("String representation of any path should be correct.", e);
		}
	}

	static BitcoinJBIP32Path fromString(String path) throws HDPathException {
		if (!HDPath.validateBIP32Path(path)) {
			throw HDPathException.invalidString;
		}
		String stringPath = path.replace(BIP32_HARDENED_MARKER_STANDARD, BIP32_HARDENED_MARKER_BITCOINJ);
		return new BitcoinJBIP32Path(org.bitcoinj.crypto.HDPath.parsePath(stringPath));
	}

	private int indexOfLastComponent() {
		return depth() - 1;
	}

	private ChildNumber lastComponent() {
		return path.get(indexOfLastComponent());
	}

	List<ChildNumber> componentsUpTo(int index) {
		return IntStream.range(0, index).mapToObj(path::get).collect(Collectors.toList());
	}

	List<ChildNumber> components() {
		return componentsUpTo(depth());
	}

	@Override
	public boolean isHardened() {
		return lastComponent().isHardened();
	}

	@Override
	public boolean hasPrivateKey() {
		return path.hasPrivateKey();
	}

	@Override
	public String toString() {
		return path.toString().replace(BIP32_HARDENED_MARKER_BITCOINJ, BIP32_HARDENED_MARKER_STANDARD);
	}

	@Override
	public int depth() {
		return path.size();
	}

	@Override
	public long index() {
		long index = (long) lastComponent().num();
		if (!isHardened()) {
			return index;
		}
		index += hardenedOffset;
		return index;
	}

	@Override
	public HDPath next() {
		ArrayList<ChildNumber> nextPathComponents = new ArrayList<>(pathListFromBIP32Path(this, indexOfLastComponent()));
		nextPathComponents.add(new ChildNumber(lastComponent().num() + 1, lastComponent().isHardened()));
		org.bitcoinj.crypto.HDPath nextPath = new org.bitcoinj.crypto.HDPath(this.hasPrivateKey(), nextPathComponents);
		return new BitcoinJBIP32Path(nextPath);
	}

	private static List<ChildNumber> pathListFromBIP32Path(BitcoinJBIP32Path path, @Nullable Integer toIndex) {
		return path.componentsUpTo(toIndex == null ? path.indexOfLastComponent() : toIndex);
	}
}
