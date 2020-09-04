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

package org.radix.serialization;

import com.radixdlt.consensus.LedgerState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.crypto.Hash;

public class BFTHeaderSerializeTest extends SerializeObject<BFTHeader> {
	public BFTHeaderSerializeTest() {
		super(BFTHeader.class, BFTHeaderSerializeTest::get);
	}

	private static BFTHeader get() {
		View view = View.of(1234567890L);
		LedgerState ledgerState = LedgerState.create(0, View.genesis(), 0, Hash.random(), 0L, false);
		return new BFTHeader(view, Hash.random(), ledgerState);
	}
}
