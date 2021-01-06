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

package com.radixdlt.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Basic unit tests for {@link UIntUtils}.
 */
public class UIntUtilsTest {

	@Test
	public void when_adding_uint384_values__the_correct_result_is_returned() {
		assertEquals(UInt384.TEN, UIntUtils.addWithOverflow(UInt384.FIVE, UInt384.FIVE));
	}

	@Test
	public void when_adding_uint256_values__the_correct_result_is_returned() {
		assertEquals(UInt384.TEN, UIntUtils.addWithOverflow(UInt384.FIVE, UInt256.FIVE));
	}

	@Test
	public void when_adding_two_uint256_values__the_correct_result_is_returned() {
		assertEquals(UInt256.TEN, UIntUtils.addWithOverflow(UInt256.FIVE, UInt256.FIVE));
	}

	@Test(expected = ArithmeticException.class)
    public void when_adding_uint384_one_to_max_value__an_exception_is_thrown() {
		UIntUtils.addWithOverflow(UInt384.MAX_VALUE, UInt384.ONE);
		fail();
	}

	@Test(expected = ArithmeticException.class)
    public void when_adding_uint256_one_to_max_value__an_exception_is_thrown() {
		UIntUtils.addWithOverflow(UInt384.MAX_VALUE, UInt256.ONE);
		fail();
	}

	@Test(expected = ArithmeticException.class)
    public void when_adding_uint256_one_to_uint256_max_value__an_exception_is_thrown() {
		UIntUtils.addWithOverflow(UInt256.MAX_VALUE, UInt256.ONE);
		fail();
	}

	@Test
	public void when_subtracting_uint384_values__the_correct_result_is_returned() {
		assertEquals(UInt384.FIVE, UIntUtils.subtractWithUnderflow(UInt384.TEN, UInt384.FIVE));
	}

	@Test
	public void when_subtracting_uint256_values__the_correct_result_is_returned() {
		assertEquals(UInt384.FIVE, UIntUtils.subtractWithUnderflow(UInt384.TEN, UInt256.FIVE));
	}

	@Test
	public void when_subtracting_two_uint256_values__the_correct_result_is_returned() {
		assertEquals(UInt256.FIVE, UIntUtils.subtractWithUnderflow(UInt256.TEN, UInt256.FIVE));
	}

	@Test(expected = ArithmeticException.class)
    public void when_subtracting_uint384_one_from_zero__an_exception_is_thrown() {
		UIntUtils.subtractWithUnderflow(UInt384.ZERO, UInt384.ONE);
		fail();
	}

	@Test(expected = ArithmeticException.class)
    public void when_subtracting_uint256_one_from_zero__an_exception_is_thrown() {
		UIntUtils.subtractWithUnderflow(UInt384.ZERO, UInt256.ONE);
		fail();
	}

	@Test(expected = ArithmeticException.class)
    public void when_subtracting_uint256_one_from_uint256_zero__an_exception_is_thrown() {
		UIntUtils.subtractWithUnderflow(UInt256.ZERO, UInt256.ONE);
		fail();
	}

	@Test
	public void when_converting_uint128_to_double__the_correct_value_is_returned() {
		// Some small values.  Note that all the long sized values are the same code path.
		// Note that 0.0 is the correct delta -> integers in this range are represented exactly.
		assertEquals(0.0, UIntUtils.toDouble(UInt128.ZERO), 0.0);
		assertEquals(1e9, UIntUtils.toDouble(UInt128.TEN.pow(9)), 0.0);

		// Check each bit works OK
		for (int i = 0; i < UInt128.SIZE; ++i) {
			UInt128 value = UInt128.TWO.pow(i);
			double dvalue = Math.pow(2.0, i);
			assertEquals(dvalue, UIntUtils.toDouble(value), 0.0); // Values are exact
		}

		// Check for rounding overflow - the big number is UInt128.MAX_VALUE.
		assertEquals(340282366920938463463374607431768211455.0, UIntUtils.toDouble(UInt128.MAX_VALUE), 0.0);
	}

}
