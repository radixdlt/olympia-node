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
import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.crypto.Signatures;

import nl.jqno.equalsverifier.EqualsVerifier;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ValidatorSetTest {

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(ValidatorSet.class)
			.verify();
	}

	@Test
	public void sensibleToString() {
		String s = ValidatorSet.from(ImmutableList.of()).toString();
		assertThat(s, containsString(ValidatorSet.class.getSimpleName()));
	}

	@Test
	public void testAcceptableFaults() {
		assertEquals(0, ValidatorSet.acceptableFaults(0));
		assertEquals(0, ValidatorSet.acceptableFaults(1));
		assertEquals(0, ValidatorSet.acceptableFaults(2));
		assertEquals(0, ValidatorSet.acceptableFaults(3));
		assertEquals(1, ValidatorSet.acceptableFaults(4));
		assertEquals(3, ValidatorSet.acceptableFaults(10));
		assertEquals(33, ValidatorSet.acceptableFaults(100));
		assertEquals(333, ValidatorSet.acceptableFaults(1000));
	}

	@Test
	public void testThreshold() {
		assertEquals(0, ValidatorSet.threshold(0));
		assertEquals(1, ValidatorSet.threshold(1));
		assertEquals(2, ValidatorSet.threshold(2));
		assertEquals(3, ValidatorSet.threshold(3));
		assertEquals(3, ValidatorSet.threshold(4));
		assertEquals(7, ValidatorSet.threshold(10));
		assertEquals(67, ValidatorSet.threshold(100));
		assertEquals(667, ValidatorSet.threshold(1000));
	}

	@Test
	public void testValidate() {
		ECKeyPair k1 = ECKeyPair.generateNew();
		ECKeyPair k2 = ECKeyPair.generateNew();
		ECKeyPair k3 = ECKeyPair.generateNew();
		ECKeyPair k4 = ECKeyPair.generateNew();
		ECKeyPair k5 = ECKeyPair.generateNew(); // Rogue signature

		Validator v1 = Validator.from(k1.getPublicKey());
		Validator v2 = Validator.from(k2.getPublicKey());
		Validator v3 = Validator.from(k3.getPublicKey());
		Validator v4 = Validator.from(k4.getPublicKey());

		ValidatorSet vs = ValidatorSet.from(ImmutableSet.of(v1, v2, v3, v4));

		Hash message = Hash.random();

		// 2 signatures for 4 validators -> fail
		Signatures sigs1 = new ECDSASignatures();
		sigs1 = sigs1.concatenate(k1.getPublicKey(), k1.sign(message));
		sigs1 = sigs1.concatenate(k2.getPublicKey(), k2.sign(message));
		ValidationResult vr1 = vs.validate(message, sigs1);
		assertFalse(vr1.valid());
		assertTrue(vr1.validators().isEmpty());

		// 3 signatures for 4 validators -> pass
		Signatures sigs2 = new ECDSASignatures();
		sigs2 = sigs2.concatenate(k1.getPublicKey(), k1.sign(message));
		sigs2 = sigs2.concatenate(k2.getPublicKey(), k2.sign(message));
		sigs2 = sigs2.concatenate(k3.getPublicKey(), k3.sign(message));
		ValidationResult vr2 = vs.validate(message, sigs2);
		assertTrue(vr2.valid());
		assertFalse(vr2.validators().isEmpty());
		assertEquals(3, vr2.validators().size());

		// 2 signatures + 1 signature not from set for 4 validators -> fail
		Signatures sigs3 = new ECDSASignatures();
		sigs3 = sigs3.concatenate(k1.getPublicKey(), k1.sign(message));
		sigs3 = sigs3.concatenate(k2.getPublicKey(), k2.sign(message));
		sigs3 = sigs3.concatenate(k5.getPublicKey(), k5.sign(message)); // key not in validator set
		ValidationResult vr3 = vs.validate(message, sigs3);
		assertFalse(vr3.valid());
		assertTrue(vr3.validators().isEmpty());
	}
}
