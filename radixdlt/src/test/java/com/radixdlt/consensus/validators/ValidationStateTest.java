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
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;

import static com.google.common.collect.Collections2.transform;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import nl.jqno.equalsverifier.EqualsVerifier;

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

	@Test
	public void testLinearSignatureValidation() {
		ECKeyPair k1 = ECKeyPair.generateNew();
		ECPublicKey kp1 = spy(k1.getPublicKey());

		ECKeyPair k2 = ECKeyPair.generateNew();
		ECPublicKey kp2 = spy(k2.getPublicKey());

		ECKeyPair k3 = ECKeyPair.generateNew();
		ECPublicKey kp3 = spy(k3.getPublicKey());

		ECKeyPair k4 = ECKeyPair.generateNew();
		ECPublicKey kp4 = spy(k4.getPublicKey());

		ECKeyPair k5 = ECKeyPair.generateNew();
		ECPublicKey kp5 = spy(k5.getPublicKey());

		ValidatorSet vset = ValidatorSet.from(transform(ImmutableList.of(kp1, kp2, kp3, kp4), Validator::from));

		Hash hash = Hash.random();

		ValidationState vstate = vset.newValidationState(hash);
		assertFalse(vstate.addSignature(kp1, k1.sign(hash)));
		assertFalse(vstate.addSignature(kp2, k2.sign(hash)));
		assertTrue(vstate.addSignature(kp3, k3.sign(hash)));
		assertTrue(vstate.addSignature(kp4, k4.sign(hash)));
		assertTrue(vstate.addSignature(kp5, k5.sign(hash)));

		assertTrue(vstate.complete());
		assertEquals(4, vstate.signatures().count());

		// verify(Hash, ECDSASignature) calls verify(Hash, byte[]), so we have to include those
		verify(kp1, times(1)).verify(any(Hash.class), any());
		verify(kp1, times(1)).verify(any(byte[].class), any());
		verify(kp2, times(1)).verify(any(Hash.class), any());
		verify(kp2, times(1)).verify(any(byte[].class), any());
		verify(kp3, times(1)).verify(any(Hash.class), any());
		verify(kp3, times(1)).verify(any(byte[].class), any());
		verify(kp4, times(1)).verify(any(Hash.class), any());
		verify(kp4, times(1)).verify(any(byte[].class), any());
		// This one was not in the validator set, so make sure we didn't verify
		verify(kp5, never()).verify(any(Hash.class), any());
		verify(kp5, never()).verify(any(byte[].class), any());

		verifyNoMoreInteractions(kp1, kp2, kp3, kp4, kp5);
	}
}
