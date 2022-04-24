/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

/** Basic unit tests for {@link UIntUtils}. */
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
    assertEquals(
        340282366920938463463374607431768211455.0, UIntUtils.toDouble(UInt128.MAX_VALUE), 0.0);
  }
}
