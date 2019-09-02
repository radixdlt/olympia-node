package org.radix.network2.addressbook;

import org.junit.Test;

import static org.junit.Assert.*;

public class PeerPredicateTest {

	private PeerPredicate truePP = p -> true;
	private PeerPredicate falsePP = p -> false;

	@Test
	public void testAnd() {
		//   A   |   B   | A ^ B
		// ------+-------+------
		// false | false | false
		// true  | false | false
		// false | true  | false
		// true  | true  | true
		assertFalse(falsePP.and(falsePP).test(null));
		assertFalse(truePP.and(falsePP).test(null));
		assertFalse(falsePP.and(truePP).test(null));
		assertTrue(truePP.and(truePP).test(null));
	}

	@Test
	public void testNegate() {
		//   A   |   ¬A
		// ------+------
		// false | true
		// true  | false
		assertTrue(falsePP.negate().test(null));
		assertFalse(truePP.negate().test(null));
	}

	@Test
	public void testOr() {
		//   A   |   B   | A v B
		// ------+-------+------
		// false | false | false
		// true  | false | true
		// false | true  | true
		// true  | true  | true
		assertFalse(falsePP.or(falsePP).test(null));
		assertTrue(truePP.or(falsePP).test(null));
		assertTrue(falsePP.or(truePP).test(null));
		assertTrue(truePP.or(truePP).test(null));
	}
}
