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

package com.radixdlt.crypto.hdwallet.bitcoinjprovider;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.radixdlt.utils.Bytes;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.MnemonicCode;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.bitcoinj.core.Utils.WHITESPACE_SPLITTER;
import static org.junit.Assert.assertEquals;

public class BIP32TestBitoinJ {

	static String ledgerAppMnemonicString = "equip will roof matter pink blind book anxiety banner elbow sun young";
	static List<String> ledgerAppMnemonicWords = WHITESPACE_SPLITTER.splitToList(ledgerAppMnemonicString);
	static byte[] ledgerAppSeed = MnemonicCode.toSeed(ledgerAppMnemonicWords, "");

	@Test
	public void abandon__11_times_concat_with__about() {
		String mnemonicString = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
		List<String> mnemonicWords = WHITESPACE_SPLITTER.splitToList(mnemonicString);
		byte[] seedBytes = MnemonicCode.toSeed(mnemonicWords, "TREZOR");
		String expectedSeedHex = "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04";
		assertEquals(expectedSeedHex, Bytes.toHexString(seedBytes));
	}

	@Test
	public void test_44ʼ︴536ʼ︴2ʼ︴1︴3() {
		DeterministicKey masterPrivateKey = HDKeyDerivation.createMasterPrivateKey(ledgerAppSeed);
		DeterministicHierarchy deterministicHierarchy = new DeterministicHierarchy(masterPrivateKey);
		assertEquals(deterministicHierarchy.getRootKey(), masterPrivateKey);
		ChildNumber[] bip32Path = new ChildNumber[]{
				new ChildNumber(44, true),
				new ChildNumber(536, true),
				new ChildNumber(2, true),
				new ChildNumber(1, false),
				new ChildNumber(3, false)
		};
		assertEquals("m/44'/536'/2'/1/3", getPathDescription(bip32Path));
		DeterministicKey childKey = deterministicHierarchy.deriveChild(
				Arrays.asList(bip32Path).subList(0, 4),
				false,
				true,
				bip32Path[bip32Path.length - 1]
		);

		assertEquals("026d5e07cfde5df84b5ef884b629d28d15b0f6c66be229680699767cd57c618288", childKey.getPublicKeyAsHex());
		assertEquals("f423ae3097703022b86b87c15424367ce827d11676fae5c7fe768de52d9cce2e", childKey.getPrivateKeyAsHex());

	}

	static String getPathDescription(ChildNumber[] bip32Path) {
		return "m/" + Joiner.on("/").join(Iterables.transform(Arrays.asList(bip32Path), p -> {
			String rawString = p.toString();
			if (rawString.endsWith("H")) {
				rawString = rawString.substring(0, rawString.length() - 1);
				rawString = rawString + "'";
			}
			return rawString;
		}
		));
	}
}
