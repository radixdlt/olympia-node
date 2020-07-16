/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.consensus.bft;

import com.radixdlt.utils.UInt256;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ValidationStateTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(ValidationState.class)
			.verify();
	}

	@Test
	public void sensibleToString() {
		String s = BFTValidatorSet.from(ImmutableList.of()).newValidationState().toString();
		assertThat(s, containsString(ValidationState.class.getSimpleName()));
	}

	@Test
	public void testAcceptableFaults() {
		assertEquals(UInt256.ZERO, ValidationState.acceptableFaults(UInt256.ZERO));
		assertEquals(UInt256.ZERO, ValidationState.acceptableFaults(UInt256.ONE));
		assertEquals(UInt256.ZERO, ValidationState.acceptableFaults(UInt256.TWO));
		assertEquals(UInt256.ZERO, ValidationState.acceptableFaults(UInt256.THREE));
		assertEquals(UInt256.ONE, ValidationState.acceptableFaults(UInt256.FOUR));
		assertEquals(UInt256.THREE, ValidationState.acceptableFaults(UInt256.TEN));
		assertEquals(UInt256.from(33), ValidationState.acceptableFaults(UInt256.from(100)));
		assertEquals(UInt256.from(333), ValidationState.acceptableFaults(UInt256.from(1000)));
	}

	@Test
	public void testThreshold() {
		assertEquals(UInt256.ZERO, ValidationState.threshold(UInt256.ZERO));
		assertEquals(UInt256.ONE, ValidationState.threshold(UInt256.ONE));
		assertEquals(UInt256.TWO, ValidationState.threshold(UInt256.TWO));
		assertEquals(UInt256.THREE, ValidationState.threshold(UInt256.THREE));
		assertEquals(UInt256.THREE, ValidationState.threshold(UInt256.FOUR));
		assertEquals(UInt256.SEVEN, ValidationState.threshold(UInt256.from(10)));
		assertEquals(UInt256.from(67), ValidationState.threshold(UInt256.from(100)));
		assertEquals(UInt256.from(667), ValidationState.threshold(UInt256.from(1000)));
	}
}
