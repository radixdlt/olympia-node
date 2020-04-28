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

import com.radixdlt.utils.UInt128;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
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
	public void testValidate() {
		ECKeyPair k1 = ECKeyPair.generateNew();
		ECKeyPair k2 = ECKeyPair.generateNew();
		ECKeyPair k3 = ECKeyPair.generateNew();
		ECKeyPair k4 = ECKeyPair.generateNew();
		ECKeyPair k5 = ECKeyPair.generateNew(); // Rogue signature

		Validator v1 = Validator.from(k1.getPublicKey(), UInt128.ONE);
		Validator v2 = Validator.from(k2.getPublicKey(), UInt128.ONE);
		Validator v3 = Validator.from(k3.getPublicKey(), UInt128.ONE);
		Validator v4 = Validator.from(k4.getPublicKey(), UInt128.ONE);

		ValidatorSet vs = ValidatorSet.from(ImmutableSet.of(v1, v2, v3, v4));
		Hash message = Hash.random();

		// 2 signatures for 4 validators -> fail
		ValidationState vst1 = vs.newValidationState(message);
		assertFalse(vst1.addSignature(k1.getPublicKey(), k1.sign(message)));
		assertFalse(vst1.addSignature(k2.getPublicKey(), k2.sign(message)));
		assertFalse(vst1.complete());
		assertEquals(2, vst1.signatures().count());

		// 3 signatures for 4 validators -> pass
		ValidationState vst2 = vs.newValidationState(message);
		assertFalse(vst2.addSignature(k1.getPublicKey(), k1.sign(message)));
		assertFalse(vst2.addSignature(k2.getPublicKey(), k2.sign(message)));
		assertTrue(vst2.addSignature(k3.getPublicKey(), k3.sign(message)));
		assertTrue(vst2.complete());
		assertEquals(3, vst2.signatures().count());

		// 2 signatures + 1 signature not from set for 4 validators -> fail
		ValidationState vst3 = vs.newValidationState(message);
		assertFalse(vst3.addSignature(k1.getPublicKey(), k1.sign(message)));
		assertFalse(vst3.addSignature(k2.getPublicKey(), k2.sign(message)));
		assertFalse(vst3.addSignature(k5.getPublicKey(), k5.sign(message)));
		assertFalse(vst3.complete());
		assertEquals(2, vst3.signatures().count());

		// 3 signatures + 1 signature not from set for 4 validators -> pass
		ValidationState vst4 = vs.newValidationState(message);
		assertFalse(vst4.addSignature(k1.getPublicKey(), k1.sign(message)));
		assertFalse(vst4.addSignature(k2.getPublicKey(), k2.sign(message)));
		assertFalse(vst4.addSignature(k5.getPublicKey(), k5.sign(message)));
		assertTrue(vst4.addSignature(k3.getPublicKey(), k3.sign(message)));
		assertTrue(vst4.complete());
		assertEquals(3, vst4.signatures().count());
	}

	@Test
	public void testValidateWithUnequalPower() {
		ECKeyPair k1 = ECKeyPair.generateNew();
		ECKeyPair k2 = ECKeyPair.generateNew();

		Validator v1 = Validator.from(k1.getPublicKey(), UInt128.THREE);
		Validator v2 = Validator.from(k2.getPublicKey(), UInt128.ONE);

		ValidatorSet vs = ValidatorSet.from(ImmutableSet.of(v1, v2));
		Hash message = Hash.random();
		ValidationState vst1 = vs.newValidationState(message);
		assertTrue(vst1.addSignature(k1.getPublicKey(), k1.sign(message)));
		assertTrue(vst1.complete());
		assertEquals(1, vst1.signatures().count());
	}
}
