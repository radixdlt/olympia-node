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

import com.radixdlt.crypto.hdwallet.HDKeyPair;
import com.radixdlt.crypto.hdwallet.HDWallet;
import org.junit.Test;


import static org.junit.Assert.assertEquals;

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
}
