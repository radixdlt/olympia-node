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

import static com.radixdlt.errors.ApiErrors.INVALID_AID_LENGTH;
import static com.radixdlt.errors.ApiErrors.INVALID_AID_STRING;
import static com.radixdlt.utils.functional.Result.fromOptional;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.hash.HashCode;
import com.google.common.primitives.UnsignedBytes;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.functional.Result;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * An Atom ID, made up of 256 bits of a hash. The Atom ID is used so that Atoms can be located using
 * just their hid.
 */
public final class AID implements Comparable<AID> {
  static final int HASH_BYTES = 32;
  public static final int BYTES = HASH_BYTES;

  public static final AID ZERO = new AID(new byte[BYTES]);

  private final byte[] value;

  private AID(byte[] bytes) {
    assert (bytes != null && bytes.length == HASH_BYTES);
    this.value = bytes;
  }

  /**
   * Copies this AID to a byte array with some offset. Note that the array must fit the offset +
   * AID.BYTES.
   *
   * @param array The array
   * @param offset The offset into that array
   */
  public void copyTo(byte[] array, int offset) {
    Objects.requireNonNull(array, "array is required");
    if (array.length - offset < BYTES) {
      throw new IllegalArgumentException(
          String.format("Array must be bigger than offset + %d but was %d", BYTES, array.length));
    }
    System.arraycopy(this.value, 0, array, offset, BYTES);
  }

  public HashCode asHashCode() {
    return HashCode.fromBytes(value);
  }

  @JsonValue
  public String toJson() {
    return Bytes.toHexString(this.value);
  }

  @Override
  public String toString() {
    return Bytes.toHexString(this.value);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof AID)) {
      return false;
    }
    if (hashCode() != o.hashCode()) {
      return false;
    }
    return Arrays.equals(this.value, ((AID) o).value);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(value);
  }

  /**
   * Gets the underlying bytes of this AID. Note that this is NOT a copy and is the actual
   * underlying byte array.
   */
  public byte[] getBytes() {
    return this.value;
  }

  /**
   * Create an AID from its bytes
   *
   * @param bytes The bytes (must be of length AID.BYTES)
   * @return An AID with those bytes
   */
  public static AID from(byte[] bytes) {
    return from(bytes, 0);
  }

  /**
   * Create an AID from a portion of a byte array
   *
   * @param bytes The bytes (must be of length AID.BYTES)
   * @param offset The offset into the bytes array
   * @return An AID with those bytes
   */
  public static AID from(byte[] bytes, int offset) {
    Objects.requireNonNull(bytes, "AID decoding error: input must not be null");
    if (offset < 0) {
      throw new IllegalArgumentException("AID decoding error: offset must be >= 0: " + offset);
    }
    if (offset + BYTES > bytes.length) {
      throw new IllegalArgumentException(
          String.format(
              "AID decoding error: length must be %d but is %d", offset + BYTES, bytes.length));
    }
    return new AID(Arrays.copyOfRange(bytes, offset, offset + BYTES));
  }

  /**
   * Create an AID from its hex bytes
   *
   * @param hexBytes The bytes in hex (must be of length AID.BYTES * 2)
   * @return An AID with those bytes
   */
  @JsonCreator
  public static AID from(String hexBytes) {
    Objects.requireNonNull(hexBytes, "hexBytes is required");
    if (hexBytes.length() != BYTES * 2) {
      throw new IllegalArgumentException(
          String.format(
              "Hex bytes string length must be %d but is %d", BYTES * 2, hexBytes.length()));
    }

    return new AID(Bytes.fromHexString(hexBytes));
  }

  /**
   * Functional style friendly version of {@link #from(String)}.
   *
   * @param input The string to parse
   * @return Success {@link Result} if value can be successfully parsed and failure {@link Result}
   *     otherwise.
   */
  public static Result<AID> fromString(String input) {
    return fromOptional(INVALID_AID_STRING, ofNullable(input))
        .filter(bytes -> bytes.length() == BYTES * 2, INVALID_AID_LENGTH)
        .map(Bytes::fromHexString)
        .flatMap(AID::fromBytes);
  }

  /**
   * Create an AID from bytes. Unlike {@link #from(byte[])} this method does not throw an exception.
   *
   * @param input The bytes (must be of length AID.BYTES)
   * @return Success result in case of successful conversion and failure result in case of error.
   */
  public static Result<AID> fromBytes(byte[] input) {
    return fromOptional(INVALID_AID_STRING, ofNullable(input))
        .filter(bytes -> bytes.length == HASH_BYTES, INVALID_AID_LENGTH)
        .map(AID::new);
  }

  @Override
  public int compareTo(AID o) {
    return lexicalComparator().compare(this, o);
  }

  private static final class LexicalComparatorHolder {
    private static final Comparator<byte[]> BYTES_COMPARATOR =
        UnsignedBytes.lexicographicalComparator();
    private static final Comparator<AID> INSTANCE =
        (o1, o2) -> BYTES_COMPARATOR.compare(o1.value, o2.value);
  }

  /** Get a lexical comparator for this type. */
  public static Comparator<AID> lexicalComparator() {
    return LexicalComparatorHolder.INSTANCE;
  }
}
