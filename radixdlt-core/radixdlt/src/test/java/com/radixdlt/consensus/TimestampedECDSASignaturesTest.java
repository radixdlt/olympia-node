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

package com.radixdlt.consensus;

import org.junit.Test;
import org.radix.serialization.SerializeObject;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;

import java.util.Collection;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;
import java.util.stream.IntStream;

import nl.jqno.equalsverifier.EqualsVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import static com.radixdlt.crypto.ECDSASignature.zeroSignature;

public class TimestampedECDSASignaturesTest extends SerializeObject<TimestampedECDSASignatures> {
	public TimestampedECDSASignaturesTest() {
		super(TimestampedECDSASignatures.class, TimestampedECDSASignaturesTest::create);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(TimestampedECDSASignatures.class)
			.verify();
	}

	@Test
	public void sensibleToString() {
		assertThat(create().toString()).contains(TimestampedECDSASignatures.class.getSimpleName());
	}

	@Test
	public void testCount() {
		assertThat(create().count()).isEqualTo(2);
	}

	@Test
	public void testSignatures() {
		assertThat(create().getSignatures()).hasSize(2);
	}

	@Test
	public void testWeightedTimeZeroWeight() {
		ImmutableList<BFTNode> keys = makeKeys(1);
		TimestampedECDSASignatures sigs = makeTimestampedECDSASignatures(keys, n -> n, n -> UInt256.ZERO);
		assertEquals(Long.MIN_VALUE, sigs.weightedTimestamp());
	}

	@Test
	public void testWeightedTime1() {
		ImmutableList<BFTNode> keys = makeKeys(1);
		TimestampedECDSASignatures sigs = makeTimestampedECDSASignatures(keys, n -> n, n -> UInt256.ONE);

		assertEquals(0L, sigs.weightedTimestamp());
	}

	@Test
	public void testWeightedTime3Equal() {
		ImmutableList<BFTNode> keys = makeKeys(3);
		TimestampedECDSASignatures sigs = makeTimestampedECDSASignatures(keys, n -> n, n -> UInt256.ONE);

		assertEquals(1L, sigs.weightedTimestamp());
	}

	@Test
	public void testWeightedTime100NonEqual() {
		ImmutableList<BFTNode> keys = makeKeys(100);
		TimestampedECDSASignatures sigs = makeTimestampedECDSASignatures(keys, n -> n, UInt256::from);

		// Using \sum_{i=0}^{n}{i} = \frac{n^2 + n}{2}, we have
		// \sum_{i=0}^{99}{i} = 4,950, so median weight = 4,950 / 2 = 2,475
		// \sum_{i=0}^{69}{i} = 2,415
		// \sum_{i=0}^{70}{i} = 2,485, so median value = 70
		assertEquals(70L, sigs.weightedTimestamp());
	}

	private ImmutableList<BFTNode> makeKeys(int n) {
		return IntStream.range(0, n)
			.mapToObj(i -> ECKeyPair.generateNew().getPublicKey())
			.map(BFTNode::create)
			.collect(ImmutableList.toImmutableList());
	}

	private TimestampedECDSASignatures makeTimestampedECDSASignatures(
		Collection<BFTNode> keys,
		LongUnaryOperator timeFunction,
		LongFunction<UInt256> weightFunction
	) {
		ImmutableMap.Builder<BFTNode, TimestampedECDSASignature> builder = ImmutableMap.builder();
		long index = 0L;
		for (BFTNode pk : keys) {
			builder.put(pk, TimestampedECDSASignature.from(
				timeFunction.applyAsLong(index),
				weightFunction.apply(index),
				zeroSignature()
			));
			index += 1;
		}
		return new TimestampedECDSASignatures(builder.build());
	}

	private static TimestampedECDSASignatures create() {
		ECPublicKey k1 = ECKeyPair.generateNew().getPublicKey();
		ECPublicKey k2 = ECKeyPair.generateNew().getPublicKey();
		ImmutableMap<BFTNode, TimestampedECDSASignature> nodeToTimestampAndSignature = ImmutableMap.of(
			BFTNode.create(k1), TimestampedECDSASignature.from(1L, UInt256.ONE, zeroSignature()),
			BFTNode.create(k2), TimestampedECDSASignature.from(1L, UInt256.ONE, zeroSignature())
		);
		return new TimestampedECDSASignatures(nodeToTimestampAndSignature);
	}
}