/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
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
