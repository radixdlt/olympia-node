/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package org.radix.common.ID;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.radix.utils.Int128;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class EUIDTest {
	private static final byte[] ONE = new byte[32];
	private static final byte[] NEGATIVE_ONE = new byte[32];
	static {
		ONE[EUID.BYTES - 1] = 1;
		Arrays.fill(NEGATIVE_ONE, (byte) 0xff);
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
		assertThat(EUID.ZERO.compareDistances(EUID.ONE, EUID.ONE), is(0));

		// Both to right of origin
		assertThat(EUID.ZERO.compareDistances(EUID.TWO, EUID.ONE), greaterThan(0));
		assertThat(EUID.ZERO.compareDistances(EUID.ONE, EUID.TWO), lessThan(0));
		EUID minusOne = new EUID(-1);
		EUID minusTwo = new EUID(-2);
		// Both to left of origin
		assertThat(EUID.ZERO.compareDistances(minusTwo, minusOne), greaterThan(0));
		assertThat(EUID.ZERO.compareDistances(minusOne, minusTwo), lessThan(0));

		// Origin between values, but different in most significant bits.
		assertThat(EUID.ZERO.compareDistances(EUID.TWO, minusOne), greaterThan(0));
		assertThat(EUID.ZERO.compareDistances(minusOne, EUID.TWO), lessThan(0));
		assertThat(EUID.ZERO.compareDistances(minusTwo, EUID.ONE), greaterThan(0));
		assertThat(EUID.ZERO.compareDistances(EUID.ONE, minusTwo), lessThan(0));

		// Origin between values, but only different in least significant bit
		EUID three = new EUID(3L);
		EUID minusThree = new EUID(-3L);
		assertThat(EUID.ZERO.compareDistances(three, minusTwo), greaterThan(0));
		assertThat(EUID.ZERO.compareDistances(minusThree, EUID.TWO), greaterThan(0));
		assertThat(EUID.ZERO.compareDistances(minusTwo, three), lessThan(0));
		assertThat(EUID.ZERO.compareDistances(EUID.TWO, minusThree), lessThan(0));

		// Check that wrap / ring behaviour works
		EUID max = new EUID(Int128.MAX_VALUE);
		EUID maxP2 = new EUID(Int128.MAX_VALUE.add(Int128.TWO));
		EUID maxM3 = new EUID(Int128.MAX_VALUE.subtract(Int128.THREE));
		assertThat(max.compareDistances(maxP2, maxM3), lessThan(0));
		assertThat(max.compareDistances(maxM3, maxP2), greaterThan(0));

		EUID min = new EUID(Int128.MIN_VALUE);
		EUID minP3 = new EUID(Int128.MIN_VALUE.add(Int128.THREE));
		EUID minM2 = new EUID(Int128.MIN_VALUE.subtract(Int128.TWO));
		assertThat(min.compareDistances(minM2, minP3), lessThan(0));
		assertThat(min.compareDistances(minP3, minM2), greaterThan(0));
	}

	/**
	 * Test routingDistanceFrom(...).
	 */
	@Test
	public void testRoutingDistanceFrom() {
		EUID minusOne = new EUID(-1L);
		assertEquals(Int128.SIZE, EUID.ZERO.routingDistanceFrom(EUID.ZERO));
		assertEquals(Int128.SIZE, EUID.ONE.routingDistanceFrom(EUID.ONE));
		assertEquals(Int128.SIZE, EUID.TWO.routingDistanceFrom(EUID.TWO));

		assertEquals(Int128.SIZE - 1, EUID.ZERO.routingDistanceFrom(EUID.ONE));
		assertEquals(Int128.SIZE - 2, EUID.ZERO.routingDistanceFrom(EUID.TWO));

		assertEquals(0, EUID.ZERO.routingDistanceFrom(minusOne));
	}
}