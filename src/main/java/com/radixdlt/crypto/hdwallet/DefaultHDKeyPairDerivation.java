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

import com.radixdlt.utils.Bytes;

import java.util.List;

public final class DefaultHDKeyPairDerivation {

	private DefaultHDKeyPairDerivation() {
		throw new IllegalStateException("Can't construct.");
	}

	public static HDKeyPairDerivation fromSeed(byte[] seed) {
		return new BitcoinJHDKeyPairDerivation(seed);
	}

	public static HDKeyPairDerivation fromSeed(String seedHex) {
		return fromSeed(Bytes.fromHexString(seedHex));
	}

	public static HDKeyPairDerivation fromMnemonicStringAndPassphrase(String mnemonic, String passphrase) throws MnemonicException {
		byte[] seed = DefaultMnemonicToSeedConverter.seedFromMnemonicStringAndPassphrase(mnemonic, passphrase);
		return fromSeed(seed);
	}

	public static HDKeyPairDerivation fromMnemonicString(String mnemonic) throws MnemonicException {
		byte[] seed = DefaultMnemonicToSeedConverter.seedFromMnemonicString(mnemonic);
		return fromSeed(seed);
	}

	public static HDKeyPairDerivation fromMnemonicWordsWithPassphrase(List<String> mnemonicWords, String passphrase) throws MnemonicException {
		byte[] seed = DefaultMnemonicToSeedConverter.seedFromMnemonicAndPassphrase(mnemonicWords, passphrase);
		return fromSeed(seed);
	}

	public static HDKeyPairDerivation fromMnemonicWords(List<String> mnemonicWords) throws MnemonicException {
		byte[] seed = DefaultMnemonicToSeedConverter.seedFromMnemonic(mnemonicWords);
		return fromSeed(seed);
	}
}
