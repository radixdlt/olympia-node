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

import java.util.Objects;

/** Utilities for manipulating primitive {@code long} values. */
public final class Longs {

  private Longs() {
    throw new IllegalStateException("Can't construct");
  }

  /**
   * Create a byte array of length {@link Long#BYTES}, and populate it with {@code value} in
   * big-endian order.
   *
   * @param value The value to convert
   * @return The resultant byte array.
   */
  public static byte[] toByteArray(long value) {
    return copyTo(value, new byte[Long.BYTES], 0);
  }

  /**
   * Copy the byte value of {@code value} into {@code bytes} starting at {@code offset}. A total of
   * {@link Long#BYTES} will be written to {@code bytes}.
   *
   * @param value The value to convert
   * @param bytes The array to write the value into
   * @param offset The offset at which to write the value
   * @return The value of {@code bytes}
   */
  public static byte[] copyTo(long value, byte[] bytes, int offset) {
    Objects.requireNonNull(bytes, "bytes is null for 'long' conversion");
    for (int i = offset + Long.BYTES - 1; i >= offset; i--) {
      bytes[i] = (byte) (value & 0xFFL);
      value >>>= 8;
    }
    return bytes;
  }

  /**
   * Exactly equivalent to {@code fromByteArray(bytes, 0)}.
   *
   * @param bytes The byte array to decode to a long
   * @return The decoded long value
   * @see #fromByteArray(byte[], int)
   */
  public static long fromByteArray(byte[] bytes) {
    return fromByteArray(bytes, 0);
  }

  /**
   * Decode a long from array {@code bytes} at {@code offset}. Bytes from array {@code
   * bytes[offset]} up to and including {@code bytes[offset + Long.BYTES - 1]} will be read from
   * array {@code bytes}.
   *
   * @param bytes The byte array to decode to a long
   * @param offset The offset within the array to start decoding
   * @return The decoded long value
   */
  public static long fromByteArray(byte[] bytes, int offset) {
    Objects.requireNonNull(bytes, "bytes is null for 'long' conversion");
    long value = 0;
    for (int b = 0; b < Long.BYTES; b++) {
      value <<= 8;
      value |= bytes[offset + b] & 0xFFL;
    }
    return value;
  }

  /**
   * Assemble a {@code long} value from it's component bytes.
   *
   * @param b0 Most significant byte
   * @param b1 Next most significant byte
   * @param b2 &hellip;
   * @param b3 &hellip;
   * @param b4 &hellip;
   * @param b5 &hellip;
   * @param b6 Next least significant byte
   * @param b7 Least significant byte
   * @return The {@code long} value represented by the arguments.
   */
  public static long fromBytes(
      byte b0, byte b1, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7) {
    return (b0 & 0xFFL) << 56
        | (b1 & 0xFFL) << 48
        | (b2 & 0xFFL) << 40
        | (b3 & 0xFFL) << 32
        | (b4 & 0xFFL) << 24
        | (b5 & 0xFFL) << 16
        | (b6 & 0xFFL) << 8
        | (b7 & 0xFFL);
  }

  // TODO simple hack for efficiently converting long array to byte array, revisit

  /**
   * Convert the given long array to an equivalent array of bytes
   *
   * @param longArray The long array
   * @return An equivalent array of bytes of length longArray.length * Long.BYTES
   */
  public static byte[] toBytes(long[] longArray) {
    // TODO optimise
    byte[] byteArray = new byte[longArray.length * Long.BYTES];
    for (int i = 0; i < longArray.length; i++) {
      copyTo(longArray[i], byteArray, i * Long.BYTES);
    }
    return byteArray;
  }

  /**
   * Convert the given byte array to an equivalent array of longs
   *
   * @param byteArray The byte array
   * @return An equivalent array of longs of length byteArray.length / Long.BYTES
   */
  public static long[] fromBytes(byte[] byteArray) {
    // TODO optimise
    long[] longArray = new long[byteArray.length / Long.BYTES];
    for (int i = 0; i < longArray.length; i++) {
      longArray[i] = fromByteArray(byteArray, i * Long.BYTES);
    }
    return longArray;
  }
}
