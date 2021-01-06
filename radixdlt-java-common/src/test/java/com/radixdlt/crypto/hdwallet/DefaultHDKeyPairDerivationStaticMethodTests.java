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
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DefaultHDKeyPairDerivationStaticMethodTests {

	static String mnemonicString = "equip will roof matter pink blind book anxiety banner elbow sun young";

	static List<String> mnemonicWords =
		Arrays.asList("equip", "will", "roof", "matter", "pink", "blind", "book", "anxiety", "banner", "elbow", "sun", "young");

	static String seedHex =
		"ed2f664e65b5ef0dd907ae15a2788cfc98e41970bc9fcb46f5900f6919862075e721f37212304a56505dab99b001cc8907ef093b7c5016a46b50c01cc3ec1cac";
	static byte[] seedBytes = Bytes.fromHexString(seedHex);

	@Test
	public void verify_same_derivation_using_mnemonicstring_no_passphrase_and_mnemonicwords() throws MnemonicException {
		assertSame(
				DefaultHDKeyPairDerivation.fromMnemonicString(mnemonicString),
				DefaultHDKeyPairDerivation.fromMnemonicWords(mnemonicWords)
		);
	}

	@Test
	public void verify_same_derivation_using_mnemonicstring_no_passphrase_and_seedHex() throws MnemonicException {
		assertSame(
				DefaultHDKeyPairDerivation.fromMnemonicString(mnemonicString),
				DefaultHDKeyPairDerivation.fromSeed(seedHex)
		);
	}

	@Test
	public void verify_same_derivation_using_mnemonicstring_no_passphrase_and_seed_bytes() throws MnemonicException {
		assertSame(
				DefaultHDKeyPairDerivation.fromMnemonicString(mnemonicString),
				DefaultHDKeyPairDerivation.fromSeed(seedBytes)
		);
	}

	@Test
	public void verify_same_derivation_using_seed_bytes_and_seed_hex() {
		assertSame(
				DefaultHDKeyPairDerivation.fromSeed(seedHex),
				DefaultHDKeyPairDerivation.fromSeed(seedBytes)
		);
	}

	private void assertSame(HDKeyPairDerivation left, HDKeyPairDerivation right) {
		String bip32Path = "m/44'/536'/2'/1/3";
		assertEquals(
				left.deriveKeyAtPath(bip32Path).privateKeyHex(),
				right.deriveKeyAtPath(bip32Path).privateKeyHex()
		);

		HDPath hdPath = null;
		try {
			hdPath = DefaultHDPath.of(bip32Path);
		} catch (HDPathException e) {
			fail("unexpected exception " + e);
			return;
		}
		assertEquals(
				left.deriveKeyAtPath(hdPath).privateKeyHex(),
				right.deriveKeyAtPath(hdPath).privateKeyHex()
		);

		assertEquals(
				left.deriveKeyAtPath(bip32Path).privateKeyHex(),
				left.deriveKeyAtPath(hdPath).privateKeyHex()
		);

		assertEquals(
				right.deriveKeyAtPath(bip32Path).privateKeyHex(),
				right.deriveKeyAtPath(hdPath).privateKeyHex()
		);
	}
}
