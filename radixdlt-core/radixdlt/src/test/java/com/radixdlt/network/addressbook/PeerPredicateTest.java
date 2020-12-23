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

package com.radixdlt.network.addressbook;

import org.junit.Test;

import static org.junit.Assert.*;

public class PeerPredicateTest {

	private PeerPredicate truePP = p -> true;
	private PeerPredicate falsePP = p -> false;

	@Test
	public void testAnd() {
		//   A   |   B   | A & B
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
		//   A   |  !A
		// ------+------
		// false | true
		// true  | false
		assertTrue(falsePP.negate().test(null));
		assertFalse(truePP.negate().test(null));
	}

	@Test
	public void testOr() {
		//   A   |   B   | A | B
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
