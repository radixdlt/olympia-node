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

	/**
	 * Creates a {@link HDKeyPairDerivation} from some {@code seed} (typically created from some
	 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP39 (BIP-39)</a> mnemonic),
	 * which can be used to derived {@link HDKeyPair} from some {@link HDPath}.
	 * @param seed, typically created from some
	 * 	 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP39 (BIP-39)</a> mnemonic.
	 * @return a {@link HDKeyPairDerivation} which an be used to derived {@link HDKeyPair} from some {@link HDPath}.
	 */
	public static HDKeyPairDerivation fromSeed(byte[] seed) {
		return new BitcoinJHDKeyPairDerivation(seed);
	}

	/**
	 * Creates a {@link HDKeyPairDerivation} from some {@code seedHex}, being a seed encoded as a hexadecimal string, where
	 * the seed is typically created from some
	 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP39 (BIP-39)</a> mnemonic.
	 * The {@link HDKeyPairDerivation} can be used to derived {@link HDKeyPair} from some {@link HDPath}.
	 * @param seedHex a seed encoded as a hexadecimal string, where the seed is typically created from some
	 *                   <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP39 (BIP-39)</a> mnemonic.
	 * @return a {@link HDKeyPairDerivation} which an be used to derived {@link HDKeyPair} from some {@link HDPath}.
	 */
	public static HDKeyPairDerivation fromSeed(String seedHex) {
		return fromSeed(Bytes.fromHexString(seedHex));
	}

	/**
	 * Creates a {@link HDKeyPairDerivation} from the
	 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP39 (BIP-39)</a> mnemonic string +
	 * {@code passphrase}. The {@link HDKeyPairDerivation} can be used to derived {@link HDKeyPair} from some {@link HDPath}.
	 * @param mnemonic a <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP39 (BIP-39)</a> mnemonic string -
	 *                    assuming a space is used to separate the words.
	 * @param passphrase A non null passphrase used together with {@code mnemonic} to form a seed.
	 * @return A {@link HDKeyPairDerivation} which an be used to derived {@link HDKeyPair} from some {@link HDPath},
	 * or if the mnemonic is invalid, a {@link MnemonicException} will be thrown.
	 * @throws MnemonicException thrown if the {@code mnemonic} is invalid.
	 */
	public static HDKeyPairDerivation fromMnemonicStringAndPassphrase(String mnemonic, String passphrase) throws MnemonicException {
		byte[] seed = DefaultMnemonicToSeedConverter.seedFromMnemonicStringAndPassphrase(mnemonic, passphrase);
		return fromSeed(seed);
	}

	/**
	 * Creates a {@link HDKeyPairDerivation} from the
	 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP39 (BIP-39)</a> mnemonic string, using
	 * the empty string as passphrase. The {@link HDKeyPairDerivation} can be used to derived {@link HDKeyPair} from some {@link HDPath}.
	 * @param mnemonic a <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP39 (BIP-39)</a> mnemonic string -
	 *                    assuming a space is used to separate the words.
	 * @return A {@link HDKeyPairDerivation} which an be used to derived {@link HDKeyPair} from some {@link HDPath},
	 * or if the mnemonic is invalid, a {@link MnemonicException} will be thrown.
	 * @throws MnemonicException thrown if the {@code mnemonic} is invalid.
	 */
	public static HDKeyPairDerivation fromMnemonicString(String mnemonic) throws MnemonicException {
		byte[] seed = DefaultMnemonicToSeedConverter.seedFromMnemonicString(mnemonic);
		return fromSeed(seed);
	}

	/**
	 * Creates a {@link HDKeyPairDerivation} from the
	 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP39 (BIP-39)</a> mnemonic words +
	 * {@code passphrase}. The {@link HDKeyPairDerivation} can be used to derived {@link HDKeyPair} from some {@link HDPath}.
	 * @param words a list of words making up a
	 *                 <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP39 (BIP-39)</a> mnemonic,
	 *              together with the {@code passphrase}.
	 * @param passphrase A non null passphrase used together with {@code words} to form a seed.
	 * @return A {@link HDKeyPairDerivation} which an be used to derived {@link HDKeyPair} from some {@link HDPath},
	 * or if the mnemonic is invalid, a {@link MnemonicException} will be thrown.
	 * @throws MnemonicException thrown if the mnemonic {@code words} are invalid.
	 */
	public static HDKeyPairDerivation fromMnemonicWordsAndPassphrase(List<String> words, String passphrase) throws MnemonicException {
		byte[] seed = DefaultMnemonicToSeedConverter.seedFromMnemonicAndPassphrase(words, passphrase);
		return fromSeed(seed);
	}

	/**
	 * Creates a {@link HDKeyPairDerivation} from the
	 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP39 (BIP-39)</a> mnemonic words.
	 * The {@link HDKeyPairDerivation} can be used to derived {@link HDKeyPair} from some {@link HDPath}.
	 * @param words a list of words making up a
	 *                 <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP39 (BIP-39)</a> mnemonic,
	 *              using the empty string as passphrase.
	 * @return A {@link HDKeyPairDerivation} which an be used to derived {@link HDKeyPair} from some {@link HDPath},
	 * or if the mnemonic is invalid, a {@link MnemonicException} will be thrown.
	 * @throws MnemonicException thrown if the mnemonic {@code words} are invalid.
	 */
	public static HDKeyPairDerivation fromMnemonicWords(List<String> words) throws MnemonicException {
		byte[] seed = DefaultMnemonicToSeedConverter.seedFromMnemonic(words);
		return fromSeed(seed);
	}
}
