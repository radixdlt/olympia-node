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

public final class DefaultMnemonicToSeedConverter {
	private  DefaultMnemonicToSeedConverter() {
		throw new IllegalStateException("Can't construct.");
	}

	public static byte[] seedFromMnemonicAndPassphrase(List<String> words, String passphrase) throws MnemonicException {
		return BitcoinJMnemonicToSeedConverter.seedFromMnemonicAndPassphrase(words, passphrase);
	}

	public static byte[] seedFromMnemonic(List<String> words) throws MnemonicException {
		return BitcoinJMnemonicToSeedConverter.seedFromMnemonic(words);
	}

	public static byte[] seedFromMnemonicStringAndPassphrase(String mnemonic, String passphrase) throws MnemonicException {
		return BitcoinJMnemonicToSeedConverter.seedFromMnemonicStringAndPassphrase(mnemonic, passphrase);
	}

	public static byte[] seedFromMnemonicString(String mnemonic) throws MnemonicException {
		return BitcoinJMnemonicToSeedConverter.seedFromMnemonicString(mnemonic);
	}
}
