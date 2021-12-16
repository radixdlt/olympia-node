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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.radixdlt.utils.Bytes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.Test;

/**
 * Test vectors for 2 rounds of SHA-256 and SHA-512.
 *
 * <p>Test vectors used have been taken from <a
 * href="https://www.di-mgt.com.au/sha_testvectors.html">
 * https://www.di-mgt.com.au/sha_testvectors.html </a>.
 *
 * <p>Note that the final 1GiB data test vector has been omitted for memory space reasons.
 */
public class SHAHashHandlerTest {

  private final MessageDigest sha256Digester = getDigester("SHA-256");
  private final MessageDigest sha512Digester = getDigester("SHA-512");
  private final SHAHashHandler hashHandler = new SHAHashHandler();

  @Test
  public void testVector1Sha256() {
    // Input message: "abc", the bit string (0x)616263 of length 24 bits.
    testSha256(
        "abc", sha256("ba7816bf 8f01cfea 414140de 5dae2223 b00361a3 96177a9c b410ff61 f20015ad"));
  }

  @Test
  public void testVector2Sha256() {
    // Input message: the empty string "", the bit string of length 0.
    testSha256(
        "", sha256("e3b0c442 98fc1c14 9afbf4c8 996fb924 27ae41e4 649b934c a495991b 7852b855"));
  }

  @Test
  public void testVector3Sha256() {
    // Input message: "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq" (length 448 bits).
    testSha256(
        "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq",
        sha256("248d6a61 d20638b8 e5c02693 0c3e6039 a33ce459 64ff2167 f6ecedd4 19db06c1"));
  }

  @Test
  public void testVector4Sha256() {
    // Input message:
    // "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu"
    // (length 896 bits).
    testSha256(
        "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu",
        sha256("cf5b16a7 78af8380 036ce59e 7b049237 0b249b11 e8f07a51 afac4503 7afee9d1"));
  }

  @Test
  public void testVector5Sha256() {
    // Input message: one million (1,000,000) repetitions of the character "a" (0x61).
    testSha256(
        "a".repeat(1_000_000),
        sha256("cdc76e5c 9914fb92 81a1c7e2 84d73e67 f1809a48 a497200e 046d39cc c7112cd0"));
  }

  @Test
  public void testVector1Sha512() {
    // Input message: "abc", the bit string (0x)616263 of length 24 bits.
    testSha512(
        "abc",
        sha512(
            "ddaf35a193617aba cc417349ae204131 12e6fa4e89a97ea2 0a9eeee64b55d39a"
                + " 2192992a274fc1a8 36ba3c23a3feebbd 454d4423643ce80e 2a9ac94fa54ca49f"));
  }

  @Test
  public void testVector2Sha512() {
    // Input message: the empty string "", the bit string of length 0.
    testSha512(
        "",
        sha512(
            "cf83e1357eefb8bd f1542850d66d8007 d620e4050b5715dc 83f4a921d36ce9ce"
                + " 47d0d13c5d85f2b0 ff8318d2877eec2f 63b931bd47417a81 a538327af927da3e"));
  }

  @Test
  public void testVector3Sha512() {
    // Input message: "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq" (length 448 bits).
    testSha512(
        "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq",
        sha512(
            "204a8fc6dda82f0a 0ced7beb8e08a416 57c16ef468b228a8 279be331a703c335"
                + " 96fd15c13b1b07f9 aa1d3bea57789ca0 31ad85c7a71dd703 54ec631238ca3445"));
  }

  @Test
  public void testVector4Sha512() {
    // Input message:
    // "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu"
    // (length 896 bits).
    testSha512(
        "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmnhijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu",
        sha512(
            "8e959b75dae313da 8cf4f72814fc143f 8f7779c6eb9f7fa1 7299aeadb6889018"
                + " 501d289e4900f7e4 331b99dec4b5433a c7d329eeb6dd2654 5e96e55b874be909"));
  }

  @Test
  public void testVector5Sha512() {
    // Input message: one million (1,000,000) repetitions of the character "a" (0x61).
    testSha512(
        "a".repeat(1_000_000),
        sha512(
            "e718483d0ce76964 4e2e42c7bc15b463 8e1f98b13b204428 5632a803afa973eb"
                + " de0ff244877ea60a 4cb0432ce577c31b eb009c5c2c49aa2e 4eadb217ad8cc09b"));
  }

  private void testSha256(String charsToHash, byte[] bytesToCompare) {
    assertEquals(32, bytesToCompare.length);
    byte[] hashedBytes = hashHandler.hash256(charsToHash.getBytes(StandardCharsets.US_ASCII));
    assertEquals(32, hashedBytes.length);
    assertArrayEquals(bytesToCompare, hashedBytes);
  }

  private void testSha512(String charsToHash, byte[] bytesToCompare) {
    assertEquals(64, bytesToCompare.length);
    byte[] hashedBytes = hashHandler.hash512(charsToHash.getBytes(StandardCharsets.US_ASCII));
    assertEquals(64, hashedBytes.length);
    assertArrayEquals(bytesToCompare, hashedBytes);
  }

  private byte[] sha256(String hexString) {
    byte[] bytes = Bytes.fromHexString(hexString.replaceAll(" ", ""));
    // Perform the second round of sha-256
    this.sha256Digester.reset();
    return this.sha256Digester.digest(bytes);
  }

  private byte[] sha512(String hexString) {
    byte[] bytes = Bytes.fromHexString(hexString.replaceAll(" ", ""));
    // Perform the second round of sha-512
    this.sha512Digester.reset();
    return this.sha512Digester.digest(bytes);
  }

  private static MessageDigest getDigester(String algorithm) {
    try {
      return MessageDigest.getInstance(algorithm);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException("No such algorithm: " + algorithm, e);
    }
  }
}
