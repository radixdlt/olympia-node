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

package com.radixdlt.identifiers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;


public class AIDTest {
	@Test
	public void testIllegalConstruction() {
		assertThatThrownBy(() -> AID.from((byte[]) null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> AID.from((String) null)).isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> AID.from(new byte[7])).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> AID.from("deadbeef")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void testCreateEquivalence() {
		byte[] bytes1 = new byte[AID.BYTES];
		for (int i = 0; i < AID.BYTES; i++) {
			bytes1[i] = (byte) i;
		}
		byte[] bytes2 = new byte[AID.BYTES];
		for (int i = 0; i < AID.BYTES; i++) {
			bytes2[i] = (byte) (AID.BYTES - i);
		}

		AID aid1 = AID.from(bytes1);
		assertArrayEquals(bytes1, aid1.getBytes());
		byte[] bytes1Copy = new byte[AID.BYTES];
		aid1.copyTo(bytes1Copy, 0);
		assertArrayEquals(bytes1Copy, bytes1);

		AID aid2 = AID.from(bytes2);
		assertArrayEquals(bytes2, aid2.getBytes());

		assertNotEquals(aid1, aid2);
	}


	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(AID.class).verify();
	}

	@Test
	public void testArrayOffsetFactory() {
		byte[] bytes = new byte[AID.BYTES * 2];
		AID aid0 = AID.from(bytes, 0);
		assertEquals(AID.ZERO, aid0);
		AID aid1 = AID.from(bytes, AID.BYTES);
		assertEquals(AID.ZERO, aid1);

		assertThatThrownBy(() -> AID.from(bytes, -1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> AID.from(bytes, AID.BYTES + 1)).isInstanceOf(IllegalArgumentException.class);
	}
}