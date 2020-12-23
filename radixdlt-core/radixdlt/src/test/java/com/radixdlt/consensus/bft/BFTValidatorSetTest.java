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

import com.google.common.hash.HashCode;
import com.radixdlt.utils.UInt256;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class BFTValidatorSetTest {

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(BFTValidatorSet.class)
			.verify();
	}

	@Test
	public void sensibleToString() {
		BFTNode node = BFTNode.create(ECKeyPair.generateNew().getPublicKey());
		String s = BFTValidatorSet.from(ImmutableList.of(BFTValidator.from(node, UInt256.ONE))).toString();
		assertThat(s, containsString(BFTValidatorSet.class.getSimpleName()));
		assertThat(s, containsString(node.getSimpleName()));
	}

	@Test
	public void testStreamConstructor() {
		BFTNode node = BFTNode.create(ECKeyPair.generateNew().getPublicKey());
		String s = BFTValidatorSet.from(ImmutableList.of(BFTValidator.from(node, UInt256.ONE)).stream()).toString();
		assertThat(s, containsString(node.getSimpleName()));
	}

	@Test
	public void testValidate() {
		ECKeyPair k1 = ECKeyPair.generateNew();
		ECKeyPair k2 = ECKeyPair.generateNew();
		ECKeyPair k3 = ECKeyPair.generateNew();
		ECKeyPair k4 = ECKeyPair.generateNew();
		ECKeyPair k5 = ECKeyPair.generateNew(); // Rogue signature

		BFTNode node1 = BFTNode.create(k1.getPublicKey());
		BFTNode node2 = BFTNode.create(k2.getPublicKey());
		BFTNode node3 = BFTNode.create(k3.getPublicKey());
		BFTNode node4 = BFTNode.create(k4.getPublicKey());
		BFTNode node5 = BFTNode.create(k5.getPublicKey());

		BFTValidator v1 = BFTValidator.from(node1, UInt256.ONE);
		BFTValidator v2 = BFTValidator.from(node2, UInt256.ONE);
		BFTValidator v3 = BFTValidator.from(node3, UInt256.ONE);
		BFTValidator v4 = BFTValidator.from(node4, UInt256.ONE);

		BFTValidatorSet vs = BFTValidatorSet.from(ImmutableSet.of(v1, v2, v3, v4));
		HashCode message = HashUtils.random256();

		// 2 signatures for 4 validators -> fail
		ValidationState vst1 = vs.newValidationState();
		assertTrue(vst1.addSignature(node1, 0L, k1.sign(message)));
		assertFalse(vst1.complete());
		assertTrue(vst1.addSignature(node2, 0L, k2.sign(message)));
		assertFalse(vst1.complete());
		assertEquals(2, vst1.signatures().count());

		// 3 signatures for 4 validators -> pass
		ValidationState vst2 = vs.newValidationState();
		assertTrue(vst2.addSignature(node1, 0L, k1.sign(message)));
		assertFalse(vst1.complete());
		assertTrue(vst2.addSignature(node2, 0L, k2.sign(message)));
		assertFalse(vst1.complete());
		assertTrue(vst2.addSignature(node3, 0L, k3.sign(message)));
		assertTrue(vst2.complete());
		assertEquals(3, vst2.signatures().count());

		// 2 signatures + 1 signature not from set for 4 validators -> fail
		ValidationState vst3 = vs.newValidationState();
		assertTrue(vst3.addSignature(node1, 0L, k1.sign(message)));
		assertFalse(vst3.complete());
		assertTrue(vst3.addSignature(node2, 0L, k2.sign(message)));
		assertFalse(vst3.complete());
		assertFalse(vst3.addSignature(node5, 0L, k5.sign(message)));
		assertFalse(vst3.complete());
		assertEquals(2, vst3.signatures().count());

		// 3 signatures + 1 signature not from set for 4 validators -> pass
		ValidationState vst4 = vs.newValidationState();
		assertTrue(vst4.addSignature(node1, 0L, k1.sign(message)));
		assertFalse(vst3.complete());
		assertTrue(vst4.addSignature(node2, 0L, k2.sign(message)));
		assertFalse(vst3.complete());
		assertFalse(vst4.addSignature(node5, 0L, k5.sign(message)));
		assertFalse(vst3.complete());
		assertTrue(vst4.addSignature(node3, 0L, k3.sign(message)));
		assertTrue(vst4.complete());
		assertEquals(3, vst4.signatures().count());
	}

	@Test
	public void testValidateWithUnequalPower() {
		ECKeyPair k1 = ECKeyPair.generateNew();
		ECKeyPair k2 = ECKeyPair.generateNew();

		BFTNode node1 = BFTNode.create(k1.getPublicKey());
		BFTNode node2 = BFTNode.create(k2.getPublicKey());

		BFTValidator v1 = BFTValidator.from(node1, UInt256.THREE);
		BFTValidator v2 = BFTValidator.from(node2, UInt256.ONE);

		BFTValidatorSet vs = BFTValidatorSet.from(ImmutableSet.of(v1, v2));
		HashCode message = HashUtils.random256();
		ValidationState vst1 = vs.newValidationState();
		assertTrue(vst1.addSignature(node1, 0L, k1.sign(message)));
		assertTrue(vst1.complete());
		assertEquals(1, vst1.signatures().count());
	}

	@Test
	public void testRetainsOrder() {
		for (var i = 0; i < 10; ++i) {
			final var validators = IntStream.range(0, 100)
				.mapToObj(n -> ECKeyPair.generateNew())
				.map(ECKeyPair::getPublicKey)
				.map(BFTNode::create)
				.map(node -> BFTValidator.from(node, UInt256.ONE))
				.collect(Collectors.toList());

			final var validatorSet = BFTValidatorSet.from(validators);
			final var setValidators = Lists.newArrayList(validatorSet.getValidators());
			checkIterableOrder(validators, setValidators);
		}
	}

	private <T> void checkIterableOrder(Iterable<T> iterable1, Iterable<T> iterable2) {
		final var i1 = iterable1.iterator();
		final var i2 = iterable2.iterator();

		while (i1.hasNext() && i2.hasNext()) {
			final var o1 = i1.next();
			final var o2 = i2.next();
			assertEquals("Objects not the same", o1, o2);
		}
		assertFalse("Iterable 1 larger than iterable 2", i1.hasNext());
		assertFalse("Iterable 2 larger than iterable 1", i2.hasNext());
	}
}
