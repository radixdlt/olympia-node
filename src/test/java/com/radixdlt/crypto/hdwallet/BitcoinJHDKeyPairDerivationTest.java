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
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.junit.Test;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BitcoinJHDKeyPairDerivationTest {

	@Test
	public void when_deriving_a_radix_bip44_path_then_the_returned_key_pair_is_correct() throws MnemonicException {
		// Same mnemonic as Ledger App
		String mnemonic = "equip will roof matter pink blind book anxiety banner elbow sun young";

		HDKeyPairDerivation hdKeyPairDerivation = DefaultHDKeyPairDerivation.fromMnemonicString(mnemonic);

		assertEquals(
				"f85f7d078c1224603ad18f19b5bcf8b127af6a3558ebcc32325cf34dc5c8bfb9",
				((BitcoinJHDKeyPairDerivation) hdKeyPairDerivation).rootPrivateKeyHex()
		);

		String bip32Path = "m/44'/536'/2'/1/3";
		HDKeyPair childKey = hdKeyPairDerivation.deriveKeyAtPath(bip32Path);
		assertEquals(bip32Path, childKey.path().toString());
		assertEquals(3L, childKey.index());
		assertEquals(5, childKey.depth());
		assertEquals(false, childKey.isHardened());

		// Expected keys are known from Leger app development.
		assertEquals("f423ae3097703022b86b87c15424367ce827d11676fae5c7fe768de52d9cce2e", childKey.privateKeyHex());
		assertEquals("026d5e07cfde5df84b5ef884b629d28d15b0f6c66be229680699767cd57c618288", childKey.publicKeyHex());

		assertEquals("m/44'/536'/2'/1/4", childKey.path().next().toString());
	}

	@Test
	public void when_deriving_a_hd_key_pair_with_a_large_index_then_then_the_returned_key_pair_is_correct() {
		// Seed from BIP32 test vector: `test_vectors_bip32.json`
		HDKeyPairDerivation hdKeyPairDerivation = DefaultHDKeyPairDerivation.fromSeed(
				"fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b7875726f6c696663605d5a5754514e4b484542"
		);

		// Key from BIP32 test vector: `test_vectors_bip32.json`
		assertEquals(
				"4b03d6fc340455b363f51020ad3ecca4f0850280cf436c70c727923f6db46c3e",
				((BitcoinJHDKeyPairDerivation) hdKeyPairDerivation).rootPrivateKeyHex()
		);

		// Path from BIP32 test vector: `test_vectors_bip32.json`
		String bip32Path = "m/0/2147483647'/1/2147483646'";
		HDKeyPair childKey = hdKeyPairDerivation.deriveKeyAtPath(bip32Path);
		assertEquals(bip32Path, childKey.path().toString());
		assertEquals(4294967294L, childKey.index());
		assertEquals(4, childKey.depth());
		assertEquals(true, childKey.isHardened());
		// Keys from BIP32 test vector: `test_vectors_bip32.json`
		assertEquals("f1c7c871a54a804afe328b4c83a1c33b8e5ff48f5087273f04efa83b247d6a2d", childKey.privateKeyHex());
		assertEquals("02d2b36900396c9282fa14628566582f206a5dd0bcc8d5e892611806cafb0301f0", childKey.publicKeyHex());

		assertEquals("m/0/2147483647'/1/2147483647'", childKey.path().next().toString());
	}


	@Test
	public void verify_all_official_bip32_test_vectors() {
		List<TestVector> vectors = testVectorsBIP32();
		vectors.forEach(this::run_test_vector);
	}

	private void run_test_vector(TestVector vector) {
		HDKeyPairDerivation hdKeyPairDerivation = vector.createHDWallet();
		assertEquals(vector.master.privateKeyHex(), ((BitcoinJHDKeyPairDerivation) hdKeyPairDerivation).rootPrivateKeyHex());
		assertEquals(vector.master.publicKeyHex(), ((BitcoinJHDKeyPairDerivation) hdKeyPairDerivation).rootPublicKeyHex());

		for (TestVector.Child childVector : vector.children) {
			HDKeyPair childKey = hdKeyPairDerivation.deriveKeyAtPath(childVector.path);
			assertEquals(childVector.privateKeyHex(), childKey.privateKeyHex());
			assertEquals(childVector.publicKeyHex(), childKey.publicKeyHex());
			assertEquals(childVector.isHardened(), childKey.isHardened());
			assertEquals(childVector.depth, childKey.depth());
			assertEquals(childVector.index(), childKey.index());
			assertTrue(childKey.path().hasPrivateKey());
		}
	}

	private List<TestVector> testVectorsBIP32() {
		Gson gson = new Gson();
		JsonReader reader = null;
		try {
			ClassLoader classLoader = getClass().getClassLoader();
			File file = new File(classLoader.getResource("test_vectors_bip32.json").getFile());
			FileReader fileReader = new FileReader(file);
			reader = new JsonReader(fileReader);
		} catch (FileNotFoundException e) {
			throw new IllegalStateException("failed to load test vectors, e: " + e);
		}
		return ((TestVectors) gson.fromJson(reader, TestVectors.class)).getVectors();
	}

	static final class TestVectors {
		List<TestVector> vectors;

		public List<TestVector> getVectors() {
			return vectors;
		}
	}

	static final class TestVector {

		static final class Master {
			private String seed;
			private String pubKey;
			private String privKey;
			private String chainCode;

			public String seedHex() {
				return seed;
			}

			public String publicKeyHex() {
				return pubKey;
			}

			public String privateKeyHex() {
				return privKey;
			}

			private byte[] privateKeyBytes() {
				return Bytes.fromHexString(privKey);
			}

			public HDKeyPairDerivation createHDWallet() {
				if (seed == null) {
					assertNotNull(chainCode);

					DeterministicKey masterPrivateKey = HDKeyDerivation.createMasterPrivKeyFromBytes(
							privateKeyBytes(),
							Bytes.fromHexString(chainCode)
					);

					return new BitcoinJHDKeyPairDerivation(masterPrivateKey);
				} else {
					return DefaultHDKeyPairDerivation.fromSeed(seed);
				}
			}
		}

		static final class Child {
			String path;
			private boolean hardened;
			private String pubKey;
			private String privKey;
			private long index;
			int depth;

			public String publicKeyHex() {
				return pubKey;
			}

			public String privateKeyHex() {
				return privKey;
			}

			public boolean isHardened() {
				return hardened;
			}

			public long index() {
				return index;
			}
		}

		Master master;
		List<Child> children;

		public HDKeyPairDerivation createHDWallet() {
			return master.createHDWallet();
		}
	}
}
