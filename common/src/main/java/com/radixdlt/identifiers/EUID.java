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

package com.radixdlt.identifiers;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.UInt128;
import java.util.Arrays;
import java.util.Objects;

public final class EUID implements Comparable<EUID> {
  public static EUID valueOf(String string) {
    return new EUID(string);
  }

  public static final EUID ZERO = new EUID(0);
  public static final EUID ONE = new EUID(1);
  public static final EUID TWO = new EUID(2);

  /** Size in bytes. */
  public static final int BYTES = UInt128.BYTES;

  private final UInt128 value;

  /**
   * Construct {@link EUID} from a {@code String} value. Hex conversion is performed.
   *
   * @param s The string to convert to an EUID.
   * @throws IllegalArgumentException If {@code s} does not contain a valid {@code EUID}.
   */
  public EUID(String s) {
    final byte[] bytes = Bytes.fromHexString(s);

    if (bytes.length != BYTES) {
      throw new IllegalArgumentException(
          "Byte length must be " + BYTES + " but was " + bytes.length);
    }

    this.value = UInt128.from(bytes);
  }

  public EUID(int value) {
    this((long) value);
  }

  public EUID(HashCode value) {
    this(value.asBytes());
  }

  public EUID(long value) {
    long extend = (value < 0) ? -1L : 0L;
    this.value = UInt128.from(extend, value);
  }

  public EUID(UInt128 value) {
    this.value = Objects.requireNonNull(value);
  }

  /**
   * Constructor for creating an {@link EUID} from an array of bytes. The array is most-significant
   * byte first, and must not be zero length.
   *
   * <p>If the array is smaller than {@link #BYTES}, then it is effectively padded with leading
   * bytes with the correct sign.
   *
   * <p>If the array is longer than {@link #BYTES}, then values at index {@link #BYTES} and beyond
   * are ignored.
   *
   * @param bytes The array of bytes to be used.
   * @throws IllegalArgumentException if {@code bytes} is 0 bytes in length.
   * @see #toByteArray()
   */
  public EUID(byte[] bytes) {
    Objects.requireNonNull(bytes);
    if (bytes.length == 0) {
      throw new IllegalArgumentException("Invalid byte length of " + bytes.length);
    }
    byte[] newBytes = extend(bytes);
    this.value = UInt128.from(newBytes);
  }

  public static EUID fromHash(HashCode hash) {
    return new EUID(hash);
  }

  /**
   * Performs hashing on {@code bytes} and creates an EUID using the 256-bit digest of those bytes.
   *
   * @param bytes
   * @return An EUID by taking the 256-bit digest of provided {@code bytes}.
   */
  public static EUID sha256(byte[] bytes) {
    return new EUID(HashUtils.sha256(bytes));
  }

  /**
   * Constructor for creating an {@link EUID} from an array of bytes. The array is most-significant
   * byte first.
   *
   * @param bytes The array of bytes to be used.
   * @param offset The offset of the bytes to be used.
   * @see #toByteArray()
   */
  public EUID(byte[] bytes, int offset) {
    Objects.requireNonNull(bytes);
    this.value = UInt128.from(bytes, offset);
  }

  /**
   * Return {@code true} if {@code this} is equal to {@link EUID.ZERO}.
   *
   * @return {@code true} if {@code this} is {@code EUID.ZERO}.
   */
  public boolean isZero() {
    return this.value.isZero();
  }

  /**
   * Retrieve the lower 64-bit word of this {@link EUID}.
   *
   * @return The lower 64-bit word of this {@link EUID}.
   */
  public long getLow() {
    return value.getLow();
  }

  /**
   * Retrieve the underlying value of this {@link EUID}
   *
   * @return The underlying {@link UInt128} value of this {@link EUID}
   */
  public UInt128 getValue() {
    return value;
  }

  /**
   * Calculate the routing difference between this {@link EUID} and the specified {@link EUID}.
   *
   * <p>Currently the algorithm is {@code numberOfLeadingZeros(this ^ other)}.
   *
   * @param other The {@link EUID} for which to calculate the routing distance from.
   * @return The routing distance.
   */
  public int routingDistanceFrom(EUID other) {
    return this.value.xor(other.value).numberOfLeadingZeros();
  }

  /**
   * Compare the distances of {@code first} and {@code second} from {@code this}, treating our
   * unsigned 128-bit number system as a ring with values 0 and 2<sup>128</sup>-1 adjacent.
   *
   * <p>The algorithm for calculating distance between two nodes is:
   *
   * <blockquote>
   *
   * distance = min<sub>unsigned</sub>((a - b) mod 2<sup>128</sup>, (b - a) mod 2<sup>128</sup>)
   *
   * </blockquote>
   *
   * @param first The first ID to calculate distance
   * @param second The second ID to calculate the distance
   * @return 0 if both distances are equal, 1 if first is further away and -1 if first is closer.
   */
  public int compareDistances(EUID first, EUID second) {
    // Trivial check now for early exit.
    if (first.equals(second)) {
      return 0;
    }
    UInt128 dFirst = ringClosest(this.value, first.value);
    UInt128 dSecond = ringClosest(this.value, second.value);
    return dFirst.compareTo(dSecond);
  }

  public int compareXorDistances(EUID id1, EUID id2) {
    UInt128 d1 = this.value.xor(id1.value);
    UInt128 d2 = this.value.xor(id2.value);

    int cmp = Integer.compare(d1.getLowestSetBit(), d2.getLowestSetBit());
    return (cmp == 0) ? d1.compareTo(d2) : cmp;
  }

  public int xorDistance(EUID id) {
    return this.value.xor(id.value).getLowestSetBit();
  }

  public byte[] toByteArray() {
    return value.toByteArray();
  }

  public byte[] toByteArray(byte[] bytes, int offset) {
    return value.toByteArray(bytes, offset);
  }

  @Override
  public int compareTo(EUID euid) {
    return this.value.compareTo(euid.value);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (o instanceof EUID) {
      EUID other = (EUID) o;
      return Objects.equals(value, other.value);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  @Override
  public String toString() {
    return Bytes.toHexString(value.toByteArray());
  }

  private static UInt128 ringClosest(UInt128 a, UInt128 b) {
    // Here we assume that our (unsigned) number system forms a ring,
    // with 0 and 2^128-1 adjacent.
    // Given this, there are two ways to get from a to b, one clockwise
    // around the ring, the other anti-clockwise (for some suitable
    // definition of cw/acw).
    // These distances are given by (a - b) mod 2^128
    // and                          (b - a) mod 2^128
    // Where mod 2^128 is implicit given that we are working with
    // 128 bit integers.
    UInt128 d1 = b.subtract(a);
    UInt128 d2 = a.subtract(b);
    return d1.compareTo(d2) <= 0 ? d1 : d2;
  }

  // Pad short (< BYTES length) array with appropriate lead bytes.
  private static byte[] extend(byte[] bytes) {
    if (bytes.length >= BYTES) {
      return bytes;
    }
    byte[] newBytes = new byte[BYTES];
    int newPos = BYTES - bytes.length;
    // Zero extension
    Arrays.fill(newBytes, 0, newPos, (bytes[0] < 0) ? (byte) 0xff : (byte) 0);
    System.arraycopy(bytes, 0, newBytes, newPos, bytes.length);
    return newBytes;
  }
}
