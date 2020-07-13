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


import org.bitcoinj.crypto.MnemonicCode;

import java.util.List;
import java.util.Objects;

import static org.bitcoinj.core.Utils.WHITESPACE_SPLITTER;

final class BitcoinJMnemonicToSeedConverter {

	private BitcoinJMnemonicToSeedConverter() {
		throw new IllegalStateException("Can't construct.");
	}

	public static byte[] seedFromMnemonicAndPassphrase(List<String> words, String passphrase) throws MnemonicException {
		try {
			MnemonicCode.INSTANCE.check(words);
		} catch (org.bitcoinj.crypto.MnemonicException e) {
			throw new MnemonicException("Mnemonic does not pass validation check", e.getCause());
		}
		Objects.requireNonNull(passphrase);
		return MnemonicCode.toSeed(words, passphrase);
	}

	public static byte[] seedFromMnemonic(List<String> words) throws MnemonicException {
		return seedFromMnemonicAndPassphrase(words, HDPaths.BIP39_MNEMONIC_NO_PASSPHRASE);
	}

	public static byte[] seedFromMnemonicStringAndPassphrase(String mnemonic, String passphrase) throws MnemonicException {
		return seedFromMnemonicAndPassphrase(WHITESPACE_SPLITTER.splitToList(mnemonic), passphrase);
	}

	public static byte[] seedFromMnemonicString(String mnemonic) throws MnemonicException {
		return seedFromMnemonicStringAndPassphrase(mnemonic, HDPaths.BIP39_MNEMONIC_NO_PASSPHRASE);
	}

}
