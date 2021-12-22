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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import org.junit.Test;

public class BytesTest {

  /** Test that partial array compares for equality work. */
  @Test
  public void testArrayEquals() {
    byte[] array1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    byte[] array2 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

    // Parts of array the same at non-matching indices
    assertTrue(Bytes.arrayEquals(array1, 1, 5, array2, 0, 5));
    // Mismatch due to length
    assertFalse(Bytes.arrayEquals(array1, 1, 5, array2, 0, 4));
    // Mismatch due to non-equal data
    assertFalse(Bytes.arrayEquals(array1, 0, 5, array2, 0, 5));
  }

  /** Test that hash codes for partial arrays are equal. */
  @Test
  public void testHashCode() {
    byte[] array1 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    byte[] array2 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

    for (int i = 0; i < 10; ++i) {
      assertEquals(Bytes.hashCode(array1, 1, i), Bytes.hashCode(array2, 0, i));
    }
  }

  /** Test conversion from byte to hex string. */
  @Test
  public void testToHexStringByte() {
    for (int i = 0; i < 0x100; ++i) {
      String base = String.format("%02x", i);
      String convert = Bytes.toHexString((byte) i);
      assertEquals(base, convert);
    }
  }

  /** Test conversion from array of bytes to hex string. */
  @Test
  public void testToHexStringByteArray() {
    byte[] bytes = new byte[256];
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 256; ++i) {
      bytes[i] = (byte) i;
      sb.append(String.format("%02x", i));
    }
    String base = sb.toString();
    assertEquals(base, Bytes.toHexString(bytes));
  }

  /** Test conversion from partial array to hex string. */
  @Test
  public void testToHexStringPartialByteArray() {
    byte[] bytes = new byte[256];
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 256; ++i) {
      bytes[i] = (byte) i;
      sb.append(String.format("%02x", i));
    }
    String base = sb.toString();

    // Offset 0, varying lengths
    for (int i = 0; i < 256; ++i) {
      assertEquals(base.substring(0, i * 2), Bytes.toHexString(bytes, 0, i));
    }
    // Varying offsets, fixed length
    for (int i = 0; i < 200; ++i) {
      assertEquals(base.substring(i * 2, i * 2 + 20), Bytes.toHexString(bytes, i, 10));
    }
  }

  /** Various test cases for conversion of string to byte array. */
  @Test
  public void testFromHexString() {
    // Single byte
    byte[] expected1 = {(byte) 0xAA};
    assertArrayEquals(expected1, Bytes.fromHexString("AA"));
    assertArrayEquals(expected1, Bytes.fromHexString("aa"));
    assertArrayEquals(expected1, Bytes.fromHexString("aA"));
    // two bytes
    byte[] expected2 = {(byte) 0xAB, (byte) 0xCD};
    assertArrayEquals(expected2, Bytes.fromHexString("ABCD"));
    assertArrayEquals(expected2, Bytes.fromHexString("abcd"));
    // two and a half bytes
    byte[] expected3 = {(byte) 0x0A, (byte) 0xBC, (byte) 0xDE};
    assertArrayEquals(expected3, Bytes.fromHexString("ABCDE"));
    assertArrayEquals(expected3, Bytes.fromHexString("abcde"));
    // four bytes
    byte[] expected4 = {(byte) 0xAB, (byte) 0xCD, (byte) 0xEF};
    assertArrayEquals(expected4, Bytes.fromHexString("ABCDEF"));
    assertArrayEquals(expected4, Bytes.fromHexString("abcdef"));
    // eight bytes
    byte[] expected8 = {0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF};
    assertArrayEquals(expected8, Bytes.fromHexString("0123456789ABCDEF"));
    assertArrayEquals(expected8, Bytes.fromHexString("0123456789abcdef"));

    // Invalid characters
    assertThatThrownBy(() -> Bytes.fromHexString("!")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Bytes.fromHexString(":")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Bytes.fromHexString("[")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Bytes.fromHexString("~")).isInstanceOf(IllegalArgumentException.class);
  }

  /** Various test cases for trimLeadingZeros. */
  @Test
  public void testTrimLeadingZeros() {
    assertEquals(null, Bytes.trimLeadingZeros(null)); // Null -> noop

    byte[] emptyBytes = new byte[0];
    assertEquals(emptyBytes, Bytes.trimLeadingZeros(emptyBytes)); // Empty -> noop

    // All size 1 byte arrays -> noop
    for (int i = 0; i < 255; ++i) {
      byte[] oneByte = new byte[1];
      oneByte[0] = (byte) i;
      assertEquals(oneByte, Bytes.trimLeadingZeros(oneByte));
    }

    // All size 2 byte arrays -> trimmed
    for (int i = 0; i < 255; ++i) {
      byte[] oneByte = new byte[1];
      byte[] twoBytes = new byte[2];
      oneByte[0] = (byte) i;
      twoBytes[1] = (byte) i;
      assertArrayEquals(oneByte, Bytes.trimLeadingZeros(twoBytes));
    }

    byte[] noLeadingZeros = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9};
    assertEquals(noLeadingZeros, Bytes.trimLeadingZeros(noLeadingZeros));

    byte[] singleZero = new byte[1];
    byte[] severalZeros = new byte[10];
    assertArrayEquals(singleZero, Bytes.trimLeadingZeros(severalZeros));

    byte[] zeroRemoved = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9};
    byte[] withZero = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    assertArrayEquals(zeroRemoved, Bytes.trimLeadingZeros(withZero));
  }

  @Test
  public void testBigIntegerToBytes() {
    final var one32 =
        new byte[] {
          0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 1
        };
    assertArrayEquals(one32, Bytes.bigIntegerToBytes(BigInteger.ONE, 32));

    final var test2 =
        new byte[] {
          0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, (byte) 2, (byte) 1
        };
    assertArrayEquals(test2, Bytes.bigIntegerToBytes(BigInteger.valueOf(513), 32));
  }

  @Test
  public void testXor() {
    final var b1 = new byte[] {1, 0, 1, 1, 1, 0, 0};
    final var b2 = new byte[] {0, 0, 0, 0, 1, 0, 1};
    assertArrayEquals(new byte[] {1, 0, 1, 1, 0, 0, 1}, Bytes.xor(b1, b2));
  }
}
