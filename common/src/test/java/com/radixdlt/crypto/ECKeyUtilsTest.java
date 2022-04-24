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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import java.math.BigInteger;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

public class ECKeyUtilsTest {

  @Test
  public void testGreaterOrEqualModulus() {
    byte[] modulus =
        ECKeyUtils.adjustArray(ECKeyUtils.domain().getN().toByteArray(), ECKeyPair.BYTES);
    assertEquals(ECKeyPair.BYTES, modulus.length);
    assertTrue(ECKeyUtils.greaterOrEqualOrder(modulus));

    byte[] goodKey =
        ECKeyUtils.adjustArray(
            ECKeyUtils.domain().getN().subtract(BigInteger.ONE).toByteArray(), ECKeyPair.BYTES);
    assertFalse(ECKeyUtils.greaterOrEqualOrder(goodKey));

    byte[] badKey = new byte[ECKeyPair.BYTES];
    Arrays.fill(badKey, (byte) 0xFF);
    assertTrue(ECKeyUtils.greaterOrEqualOrder(badKey));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGreaterOrEqualModulusFail() {
    ECKeyUtils.greaterOrEqualOrder(new byte[1]);
  }

  @Test
  public void testAdjustArray() {
    // Test that all smaller or equal lengths are padded correctly
    for (int i = 0; i <= ECKeyPair.BYTES; i += 1) {
      byte[] testArray = new byte[i];
      for (int j = 0; j < i; j += 1) {
        testArray[j] = (byte) (255 - j);
      }
      byte[] paddedArray = ECKeyUtils.adjustArray(testArray, ECKeyPair.BYTES);
      assertEquals(ECKeyPair.BYTES, paddedArray.length);
      int padding = ECKeyPair.BYTES - i;
      for (int j = 0; j < i; j += 1) {
        // Long constants because there is no assertEquals(int, int)
        assertEquals(255L - j, paddedArray[padding + j] & 0xFFL);
      }
    }
    // Test that longer length is truncated correctly
    byte[] testArray = new byte[ECKeyPair.BYTES + 1];
    for (int i = 0; i < ECKeyPair.BYTES; i += 1) {
      testArray[i + 1] = (byte) (255 - i);
    }
    byte[] truncatedArray = ECKeyUtils.adjustArray(testArray, ECKeyPair.BYTES);
    assertEquals(ECKeyPair.BYTES, truncatedArray.length);
    for (int i = 0; i < ECKeyPair.BYTES; i += 1) {
      // Long constants because there is no assertEquals(int, int)
      assertEquals(255L - i, truncatedArray[i] & 0xFFL);
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testAdjustArrayFail() {
    // Test that longer length without leading zeros throws exception
    byte[] testArray = new byte[ECKeyPair.BYTES + 1];
    Arrays.fill(testArray, (byte) 1);
    ECKeyUtils.adjustArray(testArray, ECKeyPair.BYTES);
  }

  @Test(expected = PublicKeyException.class)
  public void testValidatePublicFailForNullInput() throws PublicKeyException {
    ECKeyUtils.validatePublic(null);
  }

  @Test(expected = PublicKeyException.class)
  public void testValidatePublicFailForEmptyInput() throws PublicKeyException {
    ECKeyUtils.validatePublic(new byte[] {});
  }

  @Test
  public void testValidatePublicPassForType2Key() throws PublicKeyException {
    var key = new byte[ECPublicKey.COMPRESSED_BYTES];
    key[0] = 0x02;
    ECKeyUtils.validatePublic(key);
  }

  @Test(expected = PublicKeyException.class)
  public void testValidatePublicFailForType2Key() throws PublicKeyException {
    var key = new byte[ECPublicKey.COMPRESSED_BYTES + 1];
    key[0] = 0x02;
    ECKeyUtils.validatePublic(key);
  }

  @Test
  public void testValidatePublicPassForType3Key() throws PublicKeyException {
    var key = new byte[ECPublicKey.COMPRESSED_BYTES];
    key[0] = 0x03;
    ECKeyUtils.validatePublic(key);
  }

  @Test(expected = PublicKeyException.class)
  public void testValidatePublicFailForType3Key() throws PublicKeyException {
    var key = new byte[ECPublicKey.COMPRESSED_BYTES + 1];
    key[0] = 0x03;
    ECKeyUtils.validatePublic(key);
  }

  @Test
  public void testValidatePublicPassForType4Key() throws PublicKeyException {
    var key = new byte[ECPublicKey.UNCOMPRESSED_BYTES];
    key[0] = 0x04;
    ECKeyUtils.validatePublic(key);
  }

  @Test(expected = PublicKeyException.class)
  public void testValidatePublicFailForType4Key() throws PublicKeyException {
    var key = new byte[ECPublicKey.UNCOMPRESSED_BYTES + 1];
    key[0] = 0x04;
    ECKeyUtils.validatePublic(key);
  }

  @Test(expected = PublicKeyException.class)
  public void testValidatePublicFailForUnknownTypeKey() throws PublicKeyException {
    var key = new byte[ECPublicKey.UNCOMPRESSED_BYTES];
    key[0] = 0x05;
    ECKeyUtils.validatePublic(key);
  }

  @Test(expected = PrivateKeyException.class)
  public void testValidatePrivateFailForNullInput() throws PrivateKeyException {
    ECKeyUtils.validatePrivate(null);
  }

  @Test(expected = PrivateKeyException.class)
  public void testValidatePrivateFailForEmptyInput() throws PrivateKeyException {
    ECKeyUtils.validatePrivate(new byte[] {});
  }

  @Test(expected = PrivateKeyException.class)
  public void testValidatePrivateFailForShortInput() throws PrivateKeyException {
    ECKeyUtils.validatePrivate(new byte[ECKeyPair.BYTES - 1]);
  }

  @Test(expected = PrivateKeyException.class)
  public void testValidatePrivateFailForZeroBytes() throws PrivateKeyException {
    ECKeyUtils.validatePrivate(new byte[ECKeyPair.BYTES]);
  }

  @Test(expected = PrivateKeyException.class)
  public void testValidatePrivateFailForIncorrectOrder() throws PrivateKeyException {
    var key = new byte[ECKeyPair.BYTES];
    Arrays.fill(key, (byte) -1);
    ECKeyUtils.validatePrivate(key);
  }

  @Test
  public void testToRecoverable() throws PublicKeyException {
    var keyPair = ECKeyPair.generateNew();
    var hash = HashUtils.transactionIdHash("123456".getBytes());
    var signature = keyPair.sign(hash);

    ECKeyUtils.toRecoverable(signature, hash.asBytes(), keyPair.getPublicKey())
        .onFailureDo(Assert::fail)
        .onSuccess(
            sig ->
                ECPublicKey.recoverFrom(hash, sig)
                    .ifPresentOrElse(
                        recovered -> assertEquals(recovered, keyPair.getPublicKey()),
                        Assert::fail));
  }
}
