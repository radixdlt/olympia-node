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

package com.radixdlt.crypto;

import com.google.common.hash.HashCode;
import com.google.common.primitives.UnsignedBytes;
import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Comparator;
import org.bouncycastle.crypto.digests.KeccakDigest;

/** A class containing a collection of static methods for hashing and hashing-related utils. */
@SecurityCritical(SecurityKind.HASHING)
public final class HashUtils {
  private static final Comparator<HashCode> hashComparator =
      new Comparator<>() {
        private final Comparator<byte[]> bytesComparator =
            UnsignedBytes.lexicographicalComparator();

        @Override
        public int compare(HashCode o1, HashCode o2) {
          return bytesComparator.compare(o1.asBytes(), o2.asBytes());
        }
      };

  private static final SecureRandom secureRandom = new SecureRandom();

  private static final HashHandler shaHashHandler = new SHAHashHandler();

  private static final HashCode ZERO_256 = zero(32);

  /** Returns a hash consisting of 32 zero bytes. */
  public static HashCode zero256() {
    return ZERO_256;
  }

  /** Returns a hash consisting of {@code length} zero bytes. */
  public static HashCode zero(int length) {
    return HashCode.fromBytes(new byte[length]);
  }

  /** Returns a random hash of length 32 bytes. */
  public static HashCode random256() {
    byte[] randomBytes = new byte[32];
    secureRandom.nextBytes(randomBytes);
    return HashCode.fromBytes(shaHashHandler.hash256(randomBytes));
  }

  /**
   * Hashes the supplied array, returning a cryptographically secure 256-bit hash.
   *
   * @param dataToBeHashed The data to hash
   * @return The digest by applying the 256-bit/32-byte hash function
   */
  public static HashCode sha256(byte[] dataToBeHashed) {
    return sha256(dataToBeHashed, 0, dataToBeHashed.length);
  }

  /**
   * Hashes the specified portion of the array, returning a cryptographically secure 256-bit hash.
   *
   * @param dataToBeHashed The data to hash
   * @param offset The offset within the array to start hashing data
   * @param length The number of bytes in the array to hash
   * @return The digest by applying the 256-bit/32-byte hash function.
   */
  public static HashCode sha256(byte[] dataToBeHashed, int offset, int length) {
    return HashCode.fromBytes(shaHashHandler.hash256(dataToBeHashed, offset, length));
  }

  /**
   * Hashes the specified portion of the array, returning a cryptographically secure 512-bit hash.
   *
   * @param dataToBeHashed The data to hash
   * @return The 512-bit/64-byte hash
   */
  public static HashCode sha512(byte[] dataToBeHashed) {
    return HashCode.fromBytes(shaHashHandler.hash512(dataToBeHashed));
  }

  public static byte[] kec256(byte[]... input) {
    final var digest = new KeccakDigest(256);
    final var output = new byte[digest.getDigestSize()];
    Arrays.stream(input).forEach(i -> digest.update(i, 0, i.length));
    digest.doFinal(output, 0);
    return output;
  }

  /**
   * Compares two HashCode instances using the underlying byte array.
   *
   * @param fst The first object to be compared
   * @param snd The second object to be compared
   * @return A negative integer, zero, or a positive integer as the first argument is less than,
   *     equal to, or greater than the second.
   */
  public static int compare(HashCode fst, HashCode snd) {
    return hashComparator.compare(fst, snd);
  }

  /**
   * Calculate hash which can serve as transaction ID.
   *
   * @param payload input bytes to hash
   * @return calculated hash
   */
  public static HashCode transactionIdHash(byte[] payload) {
    return sha256(payload);
  }

  private HashUtils() {
    throw new UnsupportedOperationException();
  }
}
