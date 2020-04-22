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

package com.radixdlt.consensus.validators;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.radixdlt.crypto.Hash;

import nl.jqno.equalsverifier.EqualsVerifier;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class ValidationStateTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(ValidationState.class)
			.verify();
	}

	@Test
	public void sensibleToString() {
		Hash hash = Hash.random();
		String s = ValidatorSet.from(ImmutableList.of()).newValidationState(hash).toString();
		assertThat(s, containsString(ValidationState.class.getSimpleName()));
		assertThat(s, containsString(hash.toString()));
	}

	@Test
	public void testAcceptableFaults() {
		assertEquals(0, ValidationState.acceptableFaults(0));
		assertEquals(0, ValidationState.acceptableFaults(1));
		assertEquals(0, ValidationState.acceptableFaults(2));
		assertEquals(0, ValidationState.acceptableFaults(3));
		assertEquals(1, ValidationState.acceptableFaults(4));
		assertEquals(3, ValidationState.acceptableFaults(10));
		assertEquals(33, ValidationState.acceptableFaults(100));
		assertEquals(333, ValidationState.acceptableFaults(1000));
	}

	@Test
	public void testThreshold() {
		assertEquals(0, ValidationState.threshold(0));
		assertEquals(1, ValidationState.threshold(1));
		assertEquals(2, ValidationState.threshold(2));
		assertEquals(3, ValidationState.threshold(3));
		assertEquals(3, ValidationState.threshold(4));
		assertEquals(7, ValidationState.threshold(10));
		assertEquals(67, ValidationState.threshold(100));
		assertEquals(667, ValidationState.threshold(1000));
	}
}
