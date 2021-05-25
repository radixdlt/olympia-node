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

import com.radixdlt.atom.Txn;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atommodel.unique.state.UniqueParticle;
import com.radixdlt.utils.UInt256;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import nl.jqno.equalsverifier.EqualsVerifier;

public class FeeTableTest {
	private static final UInt256 MINIMUM_FEE = UInt256.FIVE;
	private static final ImmutableList<FeeEntry> FEE_ENTRIES = ImmutableList.of(
		PerParticleFeeEntry.of(UniqueParticle.class, 0, UInt256.THREE)
	);

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(FeeTable.class)
			.verify();
	}

	@Test
	public void testGetters() {
		FeeTable ft = get();
		assertEquals(MINIMUM_FEE, ft.minimumFee());
		assertEquals(FEE_ENTRIES, ft.feeEntries());
	}

	@Test
	public void testFeeForAtomMinimum() {
		FeeTable ft = get();
		Txn txn = TxLowLevelBuilder.newBuilder().build();
		UInt256 fee = ft.feeFor(txn, ImmutableSet.of(), 0);
		assertEquals(UInt256.FIVE, fee);
	}

	@Test
	public void testToString() {
		String s = get().toString();
		assertThat(s).contains(FeeTable.class.getSimpleName());
	}

	private static FeeTable get() {
		return FeeTable.from(MINIMUM_FEE, FEE_ENTRIES);
	}
}
