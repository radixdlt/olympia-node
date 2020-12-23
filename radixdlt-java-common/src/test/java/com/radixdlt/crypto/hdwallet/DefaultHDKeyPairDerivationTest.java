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

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class DefaultHDKeyPairDerivationTest {

	@Test
	public void when_using_mnemonic_with_empty_passphrase_and_with_no_passphrase_then_same_key_is_derived() throws MnemonicException {
		assertEquals(
		DefaultHDKeyPairDerivation.fromMnemonicStringAndPassphrase(
				"zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong",
				""
		).deriveKeyAtPath("m/0").privateKeyHex(),

				DefaultHDKeyPairDerivation.fromMnemonicString(
						"zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong"
				).deriveKeyAtPath("m/0").privateKeyHex()
		);
	}

	@Test
	public void when_using_mnemonic_words_with_empty_passphrase_and_with_no_passphrase_then_same_key_is_derived() throws MnemonicException {
		assertEquals(
				DefaultHDKeyPairDerivation.fromMnemonicWordsAndPassphrase(
						Arrays.asList(
								"zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong"
										.split(" ")),
						""
				).deriveKeyAtPath("m/0").privateKeyHex(),

				DefaultHDKeyPairDerivation.fromMnemonicWords(
						Arrays.asList(
								"zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong"
										.split(" "))
				).deriveKeyAtPath("m/0").privateKeyHex()
		);
	}

}
