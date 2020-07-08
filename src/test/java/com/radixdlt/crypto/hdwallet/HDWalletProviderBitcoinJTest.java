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
import com.radixdlt.crypto.hdwallet.HDKeyPair;
import com.radixdlt.crypto.hdwallet.HDWallet;
import com.radixdlt.crypto.hdwallet.HDWalletProviderBitcoinJ;
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
	public void test_hdwallet_44ʼ︴536ʼ︴2ʼ︴1︴3() {
	// used in the Radix DLT ledger app
		String mnemonic = "equip will roof matter pink blind book anxiety banner elbow sun young";
		HDWallet hdWallet = HDWalletProviderBitcoinJ.mnemonicNoPassphrase(mnemonic);
		HDKeyPair childKey = hdWallet.deriveKeyAtPath("m/44'/536'/2'/1/3");
		assertEquals("m/44'/536'/2'/1/3", childKey.path());
		assertEquals("f423ae3097703022b86b87c15424367ce827d11676fae5c7fe768de52d9cce2e", childKey.privateKeyHex());
		assertEquals("026d5e07cfde5df84b5ef884b629d28d15b0f6c66be229680699767cd57c618288", childKey.publicKeyHex());
	}

	@Test
	public void bip32_test_vectors() {
		List<TestVector> vectors = testVectorsBIP32();
		vectors.forEach(this::test_test_vector);
	}

	private void test_test_vector(TestVector vector) {
		HDWallet hdWallet = vector.createHDWallet();
		assertEquals(vector.master.privateKeyHex(), hdWallet.rootKeyPair().privateKeyHex());
		assertEquals(vector.master.publicKeyHex(), hdWallet.rootKeyPair().publicKeyHex());

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
				HDWallet hdWallet;
				if (seed == null) {
					assertNotNull(chainCode);
					DeterministicKey masterPrivateKey = HDKeyDerivation.createMasterPrivKeyFromBytes(privateKeyBytes(), Bytes.fromHexString(chainCode));
					hdWallet = new HDWalletProviderBitcoinJ(masterPrivateKey);
				} else {
					hdWallet = new HDWalletProviderBitcoinJ(seed);
				}
				return hdWallet;
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
