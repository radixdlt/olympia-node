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

package com.radixdlt.utils;

import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import nl.jqno.equalsverifier.EqualsVerifier;

public class PairTest {

	private Pair<Integer, String> testPair;

	@Before
	public void setUp() throws Exception {
		this.testPair = Pair.of(1234, "abcdef");
	}

	@Test
	public void equalsVerifier() {
		EqualsVerifier.forClass(Pair.class)
			.verify();
	}

	@Test
	public void testConstruction() {
		assertEquals(1234, this.testPair.getFirst().intValue());
		assertEquals("abcdef", this.testPair.getSecond());
	}

	@Test
	public void testMapFirst() {
		Function<Number, String> mapper = Number::toString;
		Pair<String, String> newPair = this.testPair.mapFirst(mapper);
		assertEquals("1234", newPair.getFirst());
		assertEquals("abcdef", newPair.getSecond());
	}

	@Test
	public void testMapSecond() {
		Function<CharSequence, Integer> mapper = s -> Integer.parseInt(s.toString(), 16);
		Pair<Integer, Integer> newPair = this.testPair.mapSecond(mapper);
		assertEquals(1234, newPair.getFirst().intValue());
		assertEquals(0xabcdef, newPair.getSecond().intValue());
	}

	@Test
	public void testFirstNonNull() {
		assertTrue(this.testPair.firstNonNull());
		assertFalse(this.testPair.firstIsNull());
	}

	@Test
	public void testFirstIsNull() {
		Pair<String, String> p = Pair.of(null, null);
		assertTrue(p.firstIsNull());
		assertFalse(p.firstNonNull());
	}

	@Test
	public void testSecondNonNull() {
		assertTrue(this.testPair.secondNonNull());
		assertFalse(this.testPair.secondIsNull());
	}

	@Test
	public void testSecondIsNull() {
		Pair<String, String> p = Pair.of(null, null);
		assertTrue(p.secondIsNull());
		assertFalse(p.secondNonNull());
	}

	@Test
	public void testToString() {
		String s = this.testPair.toString();
		assertTrue(s.startsWith(Pair.class.getSimpleName()));
		assertTrue(s.contains(this.testPair.getFirst().toString()));
		assertTrue(s.contains(this.testPair.getSecond().toString()));
	}
}
