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

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.radixdlt.utils.Bytes;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BIP39Tests {

	@Test
	public void when_validating_non_checksummed_mnemonic_with_checksum_pass_required_error_is_thrown() {
		assertThatThrownBy(() -> DefaultMnemonicToSeedConverter.validateMnemonicString(
				"abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon above"
		)).isInstanceOf(MnemonicException.class);
	}

	@Test
	public void when_validating_a_non_checksummed_mnemonic_requiring_it_to_be_checksum_test_then_it_is_invalid() {
		String nonChecksummedMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon above";
		assertFalse(DefaultMnemonicToSeedConverter.isValidMnemonicString(nonChecksummedMnemonic));
	}

	@Test
	public void when_validating_a_checksummed_mnemonic_without_checksum_test_then_it_is_valid() {
		String checksummedMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
		assertTrue(DefaultMnemonicToSeedConverter.isValidMnemonicString(checksummedMnemonic));
	}

	@Test
	public void verify_many_invalid_mnemonics() {
		List<String> invalids = invalidMnemonicStrings();
		invalids.forEach(this::assertInvalidMnemonic);
	}

	@Test
	public void when_validating_a_valid_bip39_word_list_then_it_validates() {
		assertTrue(DefaultMnemonicToSeedConverter.isValidMnemonic(Arrays.asList(
				"zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo",
				"zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "when"
		)));
	}

	@Test
	public void when_validating_a_valid_bip39_word_list_then_it_does_not_throw_an_error() {
		try {
			DefaultMnemonicToSeedConverter.validateMnemonic(Arrays.asList(
					"zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo",
					"zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "when"
			));
		} catch (MnemonicException e) {
			fail("Expected to NOT throw any exceptions but got " + e);
		}
	}

	@Test
	public void when_using_mnemonic_list_or_string_without_passphrase_they_create_the_same_seed() throws MnemonicException {
		assertEquals(
		Bytes.toHexString(DefaultMnemonicToSeedConverter.seedFromMnemonic(
				Arrays.asList(
						"zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo",
						"zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "when"
				)
				)),
				Bytes.toHexString(DefaultMnemonicToSeedConverter.seedFromMnemonicString(
						"zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo when"
				))
		);
	}

	@Test
	public void when_using_mnemonic_list_or_string_with_passphrase_they_create_the_same_seed() throws MnemonicException {
		String passphrase = "Mamma mia, here I go again";
		assertEquals(
				Bytes.toHexString(DefaultMnemonicToSeedConverter.seedFromMnemonicAndPassphrase(
						Arrays.asList(
								"zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo",
								"zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "zoo", "when"
						),
						passphrase
				)),
				Bytes.toHexString(DefaultMnemonicToSeedConverter.seedFromMnemonicStringAndPassphrase(
						"zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo when",
						passphrase
				)
			)
		);
	}

	private List<String> invalidMnemonicStrings() {
		//CHECKSTYLE:OFF
		return Arrays.asList(
				"",
				"123",
				"fjdaio;fjadisofhjdai;osfjdias;ofjdasoi;fjidsa",
				"@#!$%^%&^*&()(*&^%fdasfdaslfhuas",
				"abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon",
				"legal winner thank year wave sausage worth useful legal winner thank yellow yellow",
				"letter advice cage absurd amount doctor acoustic avoid letter advice caged above",
				"abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"
					+ " abandon abandon abandon abandon",
				"legal winner thank year wave sausage worth useful legal winner thank year wave sausage worth useful "
					+ "legal will will will",
				"letter advice cage absurd amount doctor acoustic avoid letter advice cage absurd amount doctor acoustic"
					+ " avoid letter always.",
				"zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo why",
				"abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"
					+ " abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art art",
				"legal winner thank year wave sausage worth useful legal winner thanks year wave worth useful legal winn"
					+ "er thank year wave sausage worth title",
				"letter advice cage absurd amount doctor acoustic avoid letters advice cage absurd amount doctor acoustic"
					+ " avoid letter advice cage absurd amount doctor acoustic bless",
				"zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo voted",
				"jello better achieve collect unaware mountain thought cargo oxygen act hood bridge",
				"renew, stay, biology, evidence, goat, welcome, casual, join, adapt, armor, shuffle, fault, little, "
					+ "machine, walk, stumble, urge, swap",
				"dignity pass list indicate nasty");
		//CHECKSTYLE:ON
	}

	private void assertInvalidMnemonic(String mnemonic) {
		assertFalse(
				String.format("Expecting mnemonic: <%s> to be invalid", mnemonic),
				DefaultMnemonicToSeedConverter.isValidMnemonicString(mnemonic)
		);
	}

	@Test
	public void verify_all_trezor_bip39_test_vectors() {
		List<TestVector> vectors = testVectorsBIP39();
		vectors.forEach(this::run_test_vector);
	}

	private void run_test_vector(TestVector vector) {
		String passphrase = "TREZOR";
		byte[] seedFromMnemonic = new byte[0];
		byte[] seedFromCode = new byte[0];
		try {
			seedFromMnemonic =
				DefaultMnemonicToSeedConverter.seedFromMnemonicStringAndPassphrase(vector.mnemonicString(), passphrase);
			seedFromCode =
				BitcoinJMnemonicToSeedConverter.seedFromEntropyAndPassphrase(Bytes.fromHexString(vector.entropyHex()), passphrase);
		} catch (MnemonicException e) {
			e.printStackTrace();
		}
		assertEquals(vector.bip39SeedHex(), Bytes.toHexString(seedFromMnemonic));
		assertEquals(Bytes.toHexString(seedFromCode), Bytes.toHexString(seedFromMnemonic));
		assertEquals(vector.bip32ExtendedRootKey(), new BitcoinJHDKeyPairDerivation(seedFromMnemonic).extendedRootKeyHex());
	}

	private List<TestVector> testVectorsBIP39() {
		Gson gson = new Gson();
		JsonReader reader = null;
		try {
			ClassLoader classLoader = getClass().getClassLoader();
			File file = new File(classLoader.getResource("test_vectors_bip39.json").getFile());
			FileReader fileReader = new FileReader(file);
			reader = new JsonReader(fileReader);
		} catch (FileNotFoundException e) {
			throw new IllegalStateException("failed to load test vectors, e: " + e);
		}
		return ((TestVectors) gson.fromJson(reader, TestVectors.class)).getVectors();
	}

	static final class TestVectors {
		private String[][] vectors;

		public List<TestVector> getVectors() {
			return Arrays.stream(vectors).map(TestVector::new).collect(Collectors.toList());
		}
	}

	static final class TestVector {
		private String[] strings;

		TestVector(String[] strings) {
			assert strings.length == 4;
			this.strings = strings;
		}

		String entropyHex() {
			return strings[0];
		}

		String mnemonicString() {
			return strings[1];
		}
		String bip39SeedHex() {
			return strings[2];
		}

		String bip32ExtendedRootKey() {
			return strings[3];
		}
	}
}
