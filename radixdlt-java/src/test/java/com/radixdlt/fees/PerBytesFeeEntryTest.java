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

package com.radixdlt.fees;

import static org.junit.Assert.assertEquals;
import nl.jqno.equalsverifier.EqualsVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.radixdlt.utils.UInt256;

import org.junit.Test;

public class PerBytesFeeEntryTest {
	private static final int UNITS = 1024;
	private static final int THRESHOLD = 1;
    private static final UInt256 FEE = UInt256.ONE;

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(PerBytesFeeEntry.class)
			.verify();
	}

    @Test
    public void testGetters() {
    	PerBytesFeeEntry f = get();
    	assertEquals(UNITS, f.units());
    	assertEquals(THRESHOLD, f.threshold());
    	assertEquals(FEE, f.fee());
    }

    @Test
    public void testFeeForAtom() {
    	PerBytesFeeEntry f = get();
    	assertEquals(UInt256.ZERO, f.feeFor(null, UNITS * THRESHOLD - 1, null));
    	assertEquals(UInt256.ZERO, f.feeFor(null, UNITS * THRESHOLD, null));
    	assertEquals(UInt256.ZERO, f.feeFor(null, UNITS * (THRESHOLD + 1) - 1, null));
    	assertEquals(FEE, f.feeFor(null, UNITS * (THRESHOLD + 1), null));
    	assertEquals(FEE.multiply(UInt256.TWO), f.feeFor(null, UNITS * (THRESHOLD + 2), null));
    }

    @Test
    public void boundaryConditions() {
    	// Non-positive units
    	assertThatThrownBy(() -> PerBytesFeeEntry.of(0, 0, UInt256.ONE))
    		.isInstanceOf(IllegalArgumentException.class)
    		.hasMessageStartingWith("Units must be positive");

    	// Negative threshold
    	assertThatThrownBy(() -> PerBytesFeeEntry.of(1, -1, UInt256.ONE))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageStartingWith("Threshold must be non-negative");

    	// Fee overflow
    	PerBytesFeeEntry f = PerBytesFeeEntry.of(1, 0, UInt256.MAX_VALUE);
    	assertThatThrownBy(() -> f.feeFor(null, 2, null))
    		.isInstanceOf(ArithmeticException.class)
    		.hasMessageStartingWith("Fee overflow");
    }

    @Test
    public void testToString() {
    	String s = get().toString();

    	assertThat(s).contains(PerBytesFeeEntry.class.getSimpleName());
    }

    private static PerBytesFeeEntry get() {
    	return PerBytesFeeEntry.of(UNITS, THRESHOLD, FEE);
    }
}
