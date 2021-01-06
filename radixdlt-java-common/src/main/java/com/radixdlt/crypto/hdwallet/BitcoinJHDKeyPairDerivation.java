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
import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.SecurityCritical;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.utils.Bytes;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;

import java.util.List;

@SecurityCritical({ SecurityCritical.SecurityKind.KEY_GENERATION })
public final class BitcoinJHDKeyPairDerivation implements HDKeyPairDerivation {

	/**
	 * A BIP32 extended root key.
	 */
	private final DeterministicKey bip32ExtendedRootKey;

	private final DeterministicHierarchy deterministicHierarchy;

	BitcoinJHDKeyPairDerivation(byte[] seed) {
		this(HDKeyDerivation.createMasterPrivateKey(seed));
	}

	@VisibleForTesting
	BitcoinJHDKeyPairDerivation(DeterministicKey bip32ExtendedRootKey) {
		this.bip32ExtendedRootKey = bip32ExtendedRootKey;
		this.deterministicHierarchy = new DeterministicHierarchy(bip32ExtendedRootKey);
	}

	private static List<ChildNumber> pathListFromHDPath(HDPath path) {
		return BitcoinJBIP32Path.fromPath(path).components();
	}

	private DeterministicKey deriveKeyForHDPath(HDPath path) {
		List<ChildNumber> pathList = pathListFromHDPath(path);
		return deriveKeyForPath(pathList);
	}

	private DeterministicKey deriveKeyForPath(List<ChildNumber> path) {
		return deterministicHierarchy.deriveChild(
				path.subList(0, path.size() - 1),
				false,
				true,
				path.get(path.size() - 1)
		);
	}

	@VisibleForTesting
	String rootPrivateKeyHex() {
		return Bytes.toHexString(bip32ExtendedRootKey.getPrivKeyBytes());
	}

	@VisibleForTesting
	String extendedRootKeyHex() {
		return bip32ExtendedRootKey.serializePrivB58(NetworkParameters.fromID(NetworkParameters.ID_MAINNET));
	}

	@VisibleForTesting
	String rootPublicKeyHex() {
		return Bytes.toHexString(bip32ExtendedRootKey.getPubKey());
	}

	@Override
	public HDKeyPair deriveKeyAtPath(HDPath path) {
		DeterministicKey childKey = deriveKeyForHDPath(path);
		try {
			ECKeyPair ecKeyPair = ECKeyPair.fromPrivateKey(childKey.getPrivKeyBytes());
			return new HDKeyPair(ecKeyPair, path);
		} catch (PrivateKeyException | PublicKeyException e) {
			throw new IllegalStateException("Failed to generate ECKeyPair", e);
		}
	}

}
