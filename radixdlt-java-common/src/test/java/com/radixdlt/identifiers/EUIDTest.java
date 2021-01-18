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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.base.Strings;
import com.google.common.hash.HashCode;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.UInt128;
import java.util.Arrays;
import java.util.List;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class EUIDTest {

	private static final byte[] ONE = new byte[32];
	private static final byte[] NEGATIVE_ONE = new byte[32];
	static {
		ONE[EUID.BYTES - 1] = 1;
		Arrays.fill(NEGATIVE_ONE, (byte) 0xff);
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(EUID.class).verify();
	}

	@Test
	public void testOffsetBytesConstructor() {
		EUID expected = new EUID("dead000000000000000000000000beef");
		byte[] tooManyBytes = Bytes.fromHexString("11dead000000000000000000000000beef");
		EUID offsetted = new EUID(tooManyBytes, 1); // remember 2 hex chars == 1 byte.
		assertEquals(expected, offsetted);
	}

	@Test
    public void verify_that_exception_is_thrown_when_calling_constructor_with_too_short_hexstring() {
		assertThatThrownBy(() -> new EUID("deadbeef"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
    public void verify_that_exception_is_thrown_when_calling_constructor_with_empty_byte_array() {
		assertThatThrownBy(() -> new EUID(new byte[0]))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void testZero() {
		assertTrue(EUID.ZERO.isZero());
		assertFalse(EUID.ONE.isZero());
	}

	@Test
	public void testGetValue() {
		assertEquals(UInt128.ZERO, EUID.ZERO.getValue());
		assertEquals(UInt128.ONE, EUID.ONE.getValue());
		assertEquals(UInt128.TWO, EUID.TWO.getValue());
	}

	@Test
	public void testGetLow() {
		assertEquals(1L, EUID.ONE.getLow());
		assertEquals(0L, EUID.ZERO.getLow());
		assertEquals(2L, EUID.TWO.getLow());
	}

	@Test
	public void verify_that_tobytearray_returns_same_as_passed_in_constructor() {
		byte[] bytes = Bytes.fromHexString("dead000000000000000000000000beef");
		EUID euid = new EUID(bytes);
		assertArrayEquals(bytes, euid.toByteArray());
	}

	@Test
	public void testCompare() {
		EUID low = new EUID(Strings.repeat("1", 32));
		EUID high = new EUID(Strings.repeat("9", 32));
		assertThat(low).isLessThan(high);
	}

	@Test
	public void testEqualityWithDifferentConstructors() {
		List<List<EUID>> equalIds =
			Arrays.asList(
				Arrays.asList(
					new EUID(new byte[] {0, 1}),
					new EUID(new byte[] {1}),
					new EUID(1),
					new EUID(1L),
					EUID.valueOf("00000000000000000000000000000001")
				),
				Arrays.asList(
					new EUID(new byte[] {(byte) 0xff, (byte) 0xff}),
					new EUID(new byte[] {(byte) 0xff}),
					new EUID(-1),
					new EUID(-1L),
					EUID.valueOf("ffffffffffffffffffffffffffffffff")
				)
			);

		for (int i = 0; i < equalIds.size(); i++) {
			for (int j = 0; j < equalIds.get(i).size(); j++) {
				for (int k = 0; k < equalIds.get(i).size(); k++) {
					EUID first = equalIds.get(i).get(j);
					EUID second = equalIds.get(i).get(k);
					assertEquals("Equality Test on Index " + i + " " + j + " " + k, first, second);
					assertEquals("Hash Test on Index " + i + " " + j + " " + k, first.hashCode(), second.hashCode());
					assertEquals("String Test on Index " + i + " " + j + " " + k, first.toString(), second.toString());
				}
			}
		}
	}

	@Test
	public void trimAndExpand() {
		EUID small = new EUID(Integer.MAX_VALUE);
		EUID large = new EUID(Long.MIN_VALUE);

		EUID expanded = new EUID(Arrays.copyOfRange(small.toByteArray(), EUID.BYTES - Long.BYTES, EUID.BYTES));
		EUID trimmed = new EUID(Arrays.copyOfRange(large.toByteArray(), EUID.BYTES - Long.BYTES, EUID.BYTES - Integer.BYTES));

		assertEquals(small, expanded);
		assertEquals(new EUID(Integer.MIN_VALUE), trimmed);
	}

	/**
	 * Test compareDistances().
	 */
	@Test
	public void testCompareDistances() {
		assertThat(EUID.ZERO.compareDistances(EUID.ONE, EUID.ONE)).isZero();

		// Both to right of origin
		assertThat(EUID.ZERO.compareDistances(EUID.TWO, EUID.ONE)).isPositive();
		assertThat(EUID.ZERO.compareDistances(EUID.ONE, EUID.TWO)).isNegative();
		EUID minusOne = new EUID(-1);
		EUID minusTwo = new EUID(-2);
		// Both to left of origin
		assertThat(EUID.ZERO.compareDistances(minusTwo, minusOne)).isPositive();
		assertThat(EUID.ZERO.compareDistances(minusOne, minusTwo)).isNegative();

		// Origin between values, but different in most significant bits.
		assertThat(EUID.ZERO.compareDistances(EUID.TWO, minusOne)).isPositive();
		assertThat(EUID.ZERO.compareDistances(minusOne, EUID.TWO)).isNegative();
		assertThat(EUID.ZERO.compareDistances(minusTwo, EUID.ONE)).isPositive();
		assertThat(EUID.ZERO.compareDistances(EUID.ONE, minusTwo)).isNegative();

		// Origin between values, but only different in least significant bit
		EUID three = new EUID(3L);
		EUID minusThree = new EUID(-3L);
		assertThat(EUID.ZERO.compareDistances(three, minusTwo)).isPositive();
		assertThat(EUID.ZERO.compareDistances(minusThree, EUID.TWO)).isPositive();
		assertThat(EUID.ZERO.compareDistances(minusTwo, three)).isNegative();
		assertThat(EUID.ZERO.compareDistances(EUID.TWO, minusThree)).isNegative();

		// Check that wrap / ring behaviour works
		EUID max = new EUID(UInt128.MAX_VALUE);
		EUID maxP2 = new EUID(UInt128.MAX_VALUE.add(UInt128.TWO));
		EUID maxM3 = new EUID(UInt128.MAX_VALUE.subtract(UInt128.THREE));
		assertThat(max.compareDistances(maxP2, maxM3)).isNegative();
		assertThat(max.compareDistances(maxM3, maxP2)).isPositive();

		EUID min = new EUID(UInt128.MIN_VALUE);
		EUID minP3 = new EUID(UInt128.MIN_VALUE.add(UInt128.THREE));
		EUID minM2 = new EUID(UInt128.MIN_VALUE.subtract(UInt128.TWO));
		assertThat(min.compareDistances(minM2, minP3)).isNegative();
		assertThat(min.compareDistances(minP3, minM2)).isPositive();
	}

	/**
	 * Test routingDistanceFrom(...).
	 */
	@Test
	public void testRoutingDistanceFrom() {
		EUID minusOne = new EUID(-1L);
		assertEquals(UInt128.SIZE, EUID.ZERO.routingDistanceFrom(EUID.ZERO));
		assertEquals(UInt128.SIZE, EUID.ONE.routingDistanceFrom(EUID.ONE));
		assertEquals(UInt128.SIZE, EUID.TWO.routingDistanceFrom(EUID.TWO));

		assertEquals(UInt128.SIZE - 1, EUID.ZERO.routingDistanceFrom(EUID.ONE));
		assertEquals(UInt128.SIZE - 2, EUID.ZERO.routingDistanceFrom(EUID.TWO));

		assertEquals(0, EUID.ZERO.routingDistanceFrom(minusOne));
	}

	@Test
	public void testCreateFromHashCode() {
		HashCode hash = HashCode.fromLong(1234);
		assertEquals(EUID.fromHash(hash), new EUID(hash.asBytes()));
	}
}
