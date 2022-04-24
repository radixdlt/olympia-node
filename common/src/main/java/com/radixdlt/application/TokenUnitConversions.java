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

package com.radixdlt.application;

import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt256s;
import com.radixdlt.utils.UInt384;
import java.math.BigDecimal;
import java.math.BigInteger;

/** Utility class for converting token units between UInt256 and BigDecimal */
public final class TokenUnitConversions {

  private TokenUnitConversions() {
    throw new IllegalStateException("Not initializable");
  }

  /**
   * Number of subunits in a unit as a power of 10, currently {@value #SUB_UNITS_POW_10}. In other
   * words, the total number of subunits per unit is 10<sup>{@code SUB_UNITS_POW_10}</sup>.
   */
  private static final int SUB_UNITS_POW_10 = 18;

  /**
   * Number of subunits per unit.
   *
   * @see #SUB_UNITS_POW_10
   */
  public static final UInt256 SUB_UNITS = UInt256.TEN.pow(SUB_UNITS_POW_10);

  private static final BigDecimal SUB_UNITS_BIG_DECIMAL =
      new BigDecimal(UInt256s.toBigInteger(SUB_UNITS));

  private static final BigDecimal MINIMUM_GRANULARITY_BIG_DECIMAL =
      BigDecimal.ONE.scaleByPowerOfTen(-1 * SUB_UNITS_POW_10);

  public static int getTokenScale() {
    return SUB_UNITS_POW_10;
  }

  public static BigDecimal getSubunits() {
    return SUB_UNITS_BIG_DECIMAL;
  }

  public static BigDecimal getMinimumGranularity() {
    return MINIMUM_GRANULARITY_BIG_DECIMAL;
  }

  /**
   * Returns the specified number of subunits as a fractional number of units. This method
   * effectively calculates:
   *
   * <blockquote>
   *
   * <var>subunits</var> &times; 10<sup>-SUB_UNITS_POW_10</sup>
   *
   * </blockquote>
   *
   * @param subunits The number of subunits to convert to fractional units
   * @return The number of fractional units represented by {@code subunits}
   * @see #SUB_UNITS_POW_10
   */
  public static BigDecimal subunitsToUnits(UInt256 subunits) {
    return subunitsToUnits(UInt256s.toBigInteger(subunits));
  }

  public static BigDecimal subunitsToUnits(UInt384 subunits) {
    var b = new BigInteger(1, subunits.toByteArray());
    return subunitsToUnits(b);
  }

  /**
   * Returns the specified number of subunits as a fractional number of units. This method
   * effectively calculates:
   *
   * <blockquote>
   *
   * <var>subunits</var> &times; 10<sup>-SUB_UNITS_POW_10</sup>
   *
   * </blockquote>
   *
   * @param subunits The number of subunits to convert to fractional units
   * @return The number of fractional units represented by {@code subunits}
   * @see #SUB_UNITS_POW_10
   */
  public static BigDecimal subunitsToUnits(BigInteger subunits) {
    return new BigDecimal(subunits, SUB_UNITS_POW_10);
  }

  /**
   * Returns the specified number of subunits as a fractional number of units. This method
   * effectively calculates:
   *
   * <blockquote>
   *
   * <var>subunits</var> &times; 10<sup>-SUB_UNITS_POW_10</sup>
   *
   * </blockquote>
   *
   * @param subunits The number of subunits to convert to fractional units
   * @return The number of fractional units represented by {@code subunits}
   * @see #SUB_UNITS_POW_10
   */
  public static BigDecimal subunitsToUnits(long subunits) {
    return BigDecimal.valueOf(subunits, SUB_UNITS_POW_10);
  }

  /**
   * Returns the specified number of units as a {@link UInt256} number of of subunits. This method
   * effectively calculates:
   *
   * <blockquote>
   *
   * <var>units</var> &times; 10<sup>SUB_UNITS_POW_10</sup>
   *
   * </blockquote>
   *
   * @param units The number of units to convert to subunits
   * @return The integer number of subunits represented by {@code units}
   * @throws IllegalArgumentException if {@code units} is less than zero
   * @see #SUB_UNITS_POW_10
   */
  public static UInt256 unitsToSubunits(long units) {
    if (units < 0) {
      throw new IllegalArgumentException("units must be >= 0: " + units);
    }
    // 10^18 is approximately 60 bits, so a positive long (63 bits) cannot overflow here
    return UInt256.from(units).multiply(SUB_UNITS);
  }

  /**
   * Returns the specified number of units as a {@link UInt256} number of of subunits. This method
   * effectively calculates:
   *
   * <blockquote>
   *
   * <var>units</var> &times; 10<sup>SUB_UNITS_POW_10</sup>
   *
   * </blockquote>
   *
   * @param units The number of units to convert to subunits
   * @return The integer number of subunits represented by {@code units}
   * @throws IllegalArgumentException if {@code units} is less than zero or greater than {@link
   *     UInt256#MAX_VALUE}
   * @throws ArithmeticException if {@code units} &times; 10<sup>SUB_UNITS_POW_10</sup> has a
   *     nonzero fractional part.
   * @see #SUB_UNITS_POW_10
   */
  public static UInt256 unitsToSubunits(BigDecimal units) {
    return UInt256s.fromBigDecimal(units.multiply(SUB_UNITS_BIG_DECIMAL));
  }
}
