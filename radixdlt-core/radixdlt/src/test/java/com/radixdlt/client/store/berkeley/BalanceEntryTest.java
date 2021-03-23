/*
 * (C) Copyright 2021 Radix DLT Ltd
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
package com.radixdlt.client.store.berkeley;

import org.junit.Test;

import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;

import static org.junit.Assert.assertEquals;

public class BalanceEntryTest {
	private static final RadixAddress ADDRESS = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
	private static final RRI TOKEN_RRI = RRI.of(ADDRESS, "XRD");

	@Test
	public void verifyBalanceCalculation() {
		BalanceEntry entry1 = BalanceEntry.create(ADDRESS, null, TOKEN_RRI, UInt256.ONE, UInt256.FOUR);
		BalanceEntry entry2 = BalanceEntry.create(ADDRESS, null, TOKEN_RRI, UInt256.ONE, UInt256.FIVE);

		validateDifference(entry1, entry2, UInt256.ONE, true);
		validateDifference(entry2, entry1, UInt256.ONE, false);
		validateDifference(entry1, entry2.negate(), UInt256.NINE, false);
		validateDifference(entry2, entry1.negate(), UInt256.NINE, false);
		validateDifference(entry1.negate(), entry2, UInt256.NINE, true);
		validateDifference(entry2.negate(), entry1, UInt256.NINE, true);
		validateDifference(entry1.negate(), entry2.negate(), UInt256.ONE, false);
		validateDifference(entry2.negate(), entry1.negate(), UInt256.ONE, true);
	}

	void validateDifference(BalanceEntry entry1, BalanceEntry entry2, UInt256 expectedAmount, boolean expectedNegative) {
		var difference = entry1.subtract(entry2);
		assertEquals(expectedAmount, difference.getAmount());
		assertEquals(expectedNegative, difference.isNegative());
	}
}