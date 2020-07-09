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

public class HDWalletProviderBitcoinJTest {

	@Test
	public void test_hdwallet_bip32_five_components() {
		String mnemonic = "equip will roof matter pink blind book anxiety banner elbow sun young";

		HDWallet hdWallet = DefaultHDWallet.fromMnemonicString(mnemonic);
		assertEquals("f85f7d078c1224603ad18f19b5bcf8b127af6a3558ebcc32325cf34dc5c8bfb9", ((HDWalletProviderBitcoinJ) hdWallet).rootPrivateKeyHex());
		String bip32Path = "m/44'/536'/2'/1/3";
		HDKeyPair childKey = hdWallet.deriveKeyAtPath(bip32Path);
		assertEquals(bip32Path, childKey.path());
		assertEquals("f423ae3097703022b86b87c15424367ce827d11676fae5c7fe768de52d9cce2e", childKey.privateKeyHex());
		assertEquals("026d5e07cfde5df84b5ef884b629d28d15b0f6c66be229680699767cd57c618288", childKey.publicKeyHex());
	}

	@Test
	public void bip32_test_vectors() {
		List<TestVector> vectors = testVectorsBIP32();
		vectors.forEach(this::run_test_vector);
	}

	private void run_test_vector(TestVector vector) {
		HDWallet hdWallet = vector.createHDWallet();
		assertEquals(vector.master.privateKeyHex(), ((HDWalletProviderBitcoinJ) hdWallet).rootPrivateKeyHex());
		assertEquals(vector.master.publicKeyHex(), ((HDWalletProviderBitcoinJ) hdWallet).rootPublicKeyHex());

		for (TestVector.Child childVector : vector.children) {
			HDKeyPair childKey = hdWallet.deriveKeyAtPath(childVector.path);
			assertEquals(childVector.privateKeyHex(), childKey.privateKeyHex());
			assertEquals(childVector.publicKeyHex(), childKey.publicKeyHex());
			assertEquals(childVector.isHardened(), childKey.isHardened());
			assertEquals(childVector.depth, childKey.depth());
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

			public HDWallet createHDWallet() {
				if (seed == null) {
					assertNotNull(chainCode);

					DeterministicKey masterPrivateKey = HDKeyDerivation.createMasterPrivKeyFromBytes(
							privateKeyBytes(),
							Bytes.fromHexString(chainCode)
					);

					return new HDWalletProviderBitcoinJ(masterPrivateKey);
				} else {
					return DefaultHDWallet.fromSeed(seed);
				}
			}
		}

		static final class Child {
			String path;
			private boolean hardened;
			private String pubKey;
			private String privKey;
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
		}

		Master master;
		List<Child> children;

		public HDWallet createHDWallet() {
			return master.createHDWallet();
		}
	}
}
