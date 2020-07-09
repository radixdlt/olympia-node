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

import java.util.List;

public final class DefaultHDKeyPairDerivation {

	private DefaultHDKeyPairDerivation() {
		throw new IllegalStateException("Can't construct.");
	}

	public static HDKeyPairDerivation fromSeed(byte[] seed) {
		return new BitcoinJHDKeyPairDerivation(seed);
	}

	public static HDKeyPairDerivation fromSeed(String seedHex) {
		return new BitcoinJHDKeyPairDerivation(seedHex);
	}

	public static HDKeyPairDerivation fromMnemonicStringWithPassphrase(String mnemonic, String passphrase) {
		return new BitcoinJHDKeyPairDerivation(mnemonic, passphrase);
	}

	public static HDKeyPairDerivation fromMnemonicString(String mnemonic) {
		return BitcoinJHDKeyPairDerivation.mnemonicNoPassphrase(mnemonic);
	}

	public static HDKeyPairDerivation fromMnemonicWordsWithPassphrase(List<String> mnemonicWords, String passphrase) {
		return new BitcoinJHDKeyPairDerivation(mnemonicWords, passphrase);
	}

	public static HDKeyPairDerivation fromMnemonicWords(List<String> mnemonicWords) {
		return new BitcoinJHDKeyPairDerivation(mnemonicWords);
	}
}
