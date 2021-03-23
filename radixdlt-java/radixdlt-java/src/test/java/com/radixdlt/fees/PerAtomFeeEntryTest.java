/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.fees;

import static org.junit.Assert.assertEquals;

import com.radixdlt.client.fees.PerAtomFeeEntry;
import nl.jqno.equalsverifier.EqualsVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import com.radixdlt.utils.UInt256;

import org.junit.Test;

public class PerAtomFeeEntryTest {
    private static final UInt256 FEE = UInt256.FIVE;

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(PerAtomFeeEntry.class)
			.verify();
	}

    @Test
    public void testGetters() {
    	PerAtomFeeEntry f = get();
    	assertEquals(FEE, f.fee());
    }

    @Test
    public void testFeeForAtom() {
    	PerAtomFeeEntry f = get();
    	assertEquals(FEE, f.feeFor(null, Integer.MAX_VALUE, null));
    }

    @Test
    public void testToString() {
    	String s = get().toString();
    	assertThat(s).contains(PerAtomFeeEntry.class.getSimpleName());
    }

    private static PerAtomFeeEntry get() {
    	return PerAtomFeeEntry.of(FEE);
    }
}
