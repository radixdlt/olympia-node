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

public final class DefaultHDWallet {

	private DefaultHDWallet() {
		throw new IllegalStateException("Can't construct.");
	}

	public static HDWallet fromSeed(byte[] seed) {
		return new HDWalletProviderBitcoinJ(seed);
	}

	public static HDWallet fromSeed(String seedHex) {
		return new HDWalletProviderBitcoinJ(seedHex);
	}

	public static HDWallet fromMnemonicStringWithPassphrase(String mnemonic, String passphrase) {
		return new HDWalletProviderBitcoinJ(mnemonic, passphrase);
	}

	public static HDWallet fromMnemonicString(String mnemonic) {
		return HDWalletProviderBitcoinJ.mnemonicNoPassphrase(mnemonic);
	}

	public static HDWallet fromMnemonicWordsWithPassphrase(List<String> mnemonicWords, String passphrase) {
		return new HDWalletProviderBitcoinJ(mnemonicWords, passphrase);
	}

	public static HDWallet fromMnemonicWords(List<String> mnemonicWords) {
		return new HDWalletProviderBitcoinJ(mnemonicWords);
	}
}
