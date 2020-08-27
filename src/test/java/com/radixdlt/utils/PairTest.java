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
