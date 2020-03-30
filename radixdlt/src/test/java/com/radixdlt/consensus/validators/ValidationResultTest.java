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
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;

import nl.jqno.equalsverifier.EqualsVerifier;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ValidationResultTest {

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(ValidationResult.class)
			.verify();
	}

	@Test
	public void sensibleToString() {
		String s = ValidationResult.failure().toString();
		assertThat(s, containsString(ValidationResult.class.getSimpleName()));
	}

	@Test
	public void testGetters() throws CryptoException {
		assertFalse(ValidationResult.failure().valid());

		ECKeyPair nodeKey = ECKeyPair.generateNew();
		Validator v = Validator.from(nodeKey.getPublicKey());
		ImmutableList<Validator> vs = ImmutableList.of(v);
		ValidationResult vr = ValidationResult.passed(vs);
		assertTrue(vr.valid());
		assertEquals(vs, vr.validators());
	}
}
