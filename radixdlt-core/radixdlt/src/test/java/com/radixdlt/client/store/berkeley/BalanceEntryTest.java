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

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import static org.junit.Assert.assertEquals;

public class BalanceEntryTest {
	private static final RadixAddress ADDRESS = RadixAddress.from("JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor");
	private static final RRI TOKEN_RRI = RRI.of(ADDRESS, "XRD");

	@Test
	public void verifyBalanceCalculation() {
		BalanceEntry entry1 = BalanceEntry.createBalance(ADDRESS, null, TOKEN_RRI, UInt256.ONE, UInt256.FOUR);
		BalanceEntry entry2 = BalanceEntry.createBalance(ADDRESS, null, TOKEN_RRI, UInt256.ONE, UInt256.FIVE);

		validate(entry1, entry2, UInt256.NINE, false);           		// 4 + 5 => 9
		validate(entry2, entry1, UInt256.NINE, false);               	// 5 + 4 => 9
		validate(entry1, entry2.negate(), UInt256.ONE, true);        	// 4 + (-5) = -1
		validate(entry2, entry1.negate(), UInt256.ONE, false);    		// 5 + (-4) = 1
		validate(entry1.negate(), entry2, UInt256.ONE, false);    		// -4 + 5 = 1
		validate(entry2.negate(), entry1, UInt256.ONE, true);    		// -5 + 4 = -1
		validate(entry1.negate(), entry2.negate(), UInt256.NINE, true);  // -4 + (-5) = -9
		validate(entry2.negate(), entry1.negate(), UInt256.NINE, true);	// -5 + (-4) = -9
	}

	void validate(BalanceEntry entry1, BalanceEntry entry2, UInt256 expectedAmount, boolean expectedNegative) {
		var sum = entry1.add(entry2);
		assertEquals(expectedAmount, sum.getAmount());
		assertEquals(format(entry1) + " + " + format(entry2) + " = " + format(sum), expectedNegative, sum.isNegative());
	}

	private String format(BalanceEntry be) {
		var prefix = be.isNegative() ? "(-" : "";
		var suffix = be.isNegative() ? ")" : "";

		return prefix + be.getAmount().toString() + suffix;
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(BalanceEntry.class)
			.suppress(Warning.NONFINAL_FIELDS)
			.withIgnoredFields("supply")
			.withNonnullFields("owner", "rri", "granularity", "amount")
			.verify();
	}
}