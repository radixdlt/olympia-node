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

import nl.jqno.equalsverifier.EqualsVerifier;

import org.junit.Test;
import org.radix.serialization.SerializeObject;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.Pair;

import static org.assertj.core.api.Assertions.*;

public class TimestampedECDSASignaturesTest extends  SerializeObject<TimestampedECDSASignatures> {
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

	private static TimestampedECDSASignatures create() {
		ECPublicKey k1 = ECKeyPair.generateNew().getPublicKey();
		ECPublicKey k2 = ECKeyPair.generateNew().getPublicKey();
		ImmutableMap<BFTNode, Pair<Long, ECDSASignature>> keyToTimestampAndSignature = ImmutableMap.of(
			BFTNode.create(k1), Pair.of(1L, new ECDSASignature()),
			BFTNode.create(k2), Pair.of(2L, new ECDSASignature())
		);
		return new TimestampedECDSASignatures(keyToTimestampAndSignature);
	}
}