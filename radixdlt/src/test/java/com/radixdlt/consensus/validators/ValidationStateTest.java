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

import com.radixdlt.utils.UInt256;
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

		ValidatorSet vset = ValidatorSet.from(transform(ImmutableList.of(kp1, kp2, kp3, kp4), v -> Validator.from(v, UInt256.ONE)));

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
