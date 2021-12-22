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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.radixdlt.TestSetupUtils;
import com.radixdlt.crypto.exception.KeyStoreException;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.stream.IntStream;
import javax.crypto.SecretKey;
import org.bouncycastle.jcajce.PKCS12Key;
import org.junit.BeforeClass;
import org.junit.Test;

public class RadixKeyStoreTest {

  private static final String TEST_SECRET = "secret";
  private static final String TEST_KS_FILENAME = "testfile.ks";

  @BeforeClass
  public static void setup() {
    TestSetupUtils.installBouncyCastleProvider();
  }

  /** Test method for {@link RadixKeyStore#fromFile(java.io.File, char[], boolean)}. */
  @Test
  public void testFromFileCreate() throws IOException, KeyStoreException {
    File file = newFile(TEST_KS_FILENAME);
    try (RadixKeyStore ks = RadixKeyStore.fromFile(file, null, true)) {
      assertTrue(file.exists());
      assertTrue(ks.toString().contains(file.toString()));
    }
  }

  /** Test method for {@link RadixKeyStore#fromFile(java.io.File, char[], boolean)}. */
  @Test
  public void testFromFileNotFound() throws IOException {
    File file = newFile(TEST_KS_FILENAME);
    assertThatThrownBy(() -> RadixKeyStore.fromFile(file, null, false))
        .isInstanceOf(FileNotFoundException.class);
  }

  /** Test method for {@link RadixKeyStore#fromFile(java.io.File, char[], boolean)}. */
  @Test
  public void testFromFileReload1()
      throws IOException, KeyStoreException, PrivateKeyException, PublicKeyException {
    File file = newFile(TEST_KS_FILENAME);
    ECKeyPair kp1 = ECKeyPair.generateNew();
    try (RadixKeyStore ks = RadixKeyStore.fromFile(file, null, true)) {
      assertTrue(file.exists());
      ks.writeKeyPair("test", kp1);
    }
    try (RadixKeyStore ks = RadixKeyStore.fromFile(file, null, false)) {
      ECKeyPair kp2 = ks.readKeyPair("test", false);
      assertArrayEquals(kp1.getPrivateKey(), kp2.getPrivateKey());
      assertArrayEquals(kp1.getPublicKey().getBytes(), kp2.getPublicKey().getBytes());
    }
  }

  /** Test method for {@link RadixKeyStore#fromFile(java.io.File, char[], boolean)}. */
  @Test
  public void testFromFileReload2()
      throws IOException, KeyStoreException, PrivateKeyException, PublicKeyException {
    File file = newFile(TEST_KS_FILENAME);
    final ECKeyPair kp1;
    try (RadixKeyStore ks = RadixKeyStore.fromFile(file, null, true)) {
      assertTrue(file.exists());
      kp1 = ks.readKeyPair("test", true);
    }
    try (RadixKeyStore ks = RadixKeyStore.fromFile(file, new char[0], false)) {
      ECKeyPair kp2 = ks.readKeyPair("test", false);
      assertArrayEquals(kp1.getPrivateKey(), kp2.getPrivateKey());
      assertArrayEquals(kp1.getPublicKey().getBytes(), kp2.getPublicKey().getBytes());
    }
  }

  /** Test method for {@link RadixKeyStore#fromFile(java.io.File, char[], boolean)}. */
  @Test
  public void testFromFileWrongFilePassword() throws IOException, KeyStoreException {
    File file = newFile(TEST_KS_FILENAME);
    try (RadixKeyStore ks = RadixKeyStore.fromFile(file, "secret1".toCharArray(), true)) {
      assertTrue(file.exists());
      assertTrue(ks.toString().contains(file.toString()));
    }
    assertThatThrownBy(() -> RadixKeyStore.fromFile(file, "secret2".toCharArray(), false))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("wrong password")
        .hasNoCause();
  }

  /** Test method for {@link RadixKeyStore#readKeyPair(String, boolean)}. */
  @Test
  public void testReadKeyPairFail() throws IOException, KeyStoreException {
    File file = newFile(TEST_KS_FILENAME);
    try (RadixKeyStore ks = RadixKeyStore.fromFile(file, null, true)) {
      assertTrue(file.exists());
      assertThatThrownBy(() -> ks.readKeyPair("notexist", false))
          .isInstanceOf(KeyStoreException.class)
          .hasMessageContaining("No such entry")
          .hasNoCause();
    }
  }

  /** Test method for {@link RadixKeyStore#readKeyPair(String, boolean)}. */
  @Test
  public void testReadKeyPairSuccess()
      throws IOException, KeyStoreException, PrivateKeyException, PublicKeyException {
    final File file = newFile("keystore.ks");
    final var originalKeypair = ECKeyPair.generateNew();
    final var storePassword = "nopass".toCharArray();
    final var keyPairName = "node";

    try (RadixKeyStore ks = RadixKeyStore.fromFile(file, storePassword, true)) {
      assertTrue(file.exists());

      ks.writeKeyPair(keyPairName, originalKeypair);
    }

    final File renamedFile = new File(TEST_KS_FILENAME);
    file.renameTo(renamedFile);

    try (RadixKeyStore ks = RadixKeyStore.fromFile(renamedFile, storePassword, false)) {
      var loadedKeypair = ks.readKeyPair(keyPairName, false);
      assertThat(loadedKeypair).isEqualTo(originalKeypair);
    }
  }

  /** Test method for {@link RadixKeyStore#close()}. */
  @Test
  public void testClose() throws IOException, KeyStoreException {
    File file = newFile(TEST_KS_FILENAME);
    @SuppressWarnings("resource")
    RadixKeyStore ks = RadixKeyStore.fromFile(file, "testpassword".toCharArray(), true);
    assertTrue(file.exists());
    ks.close();
    char[] pwd = ks.storePassword();
    assertEquals(12, pwd.length);
    assertTrue(IntStream.range(0, pwd.length).map(i -> pwd[i]).allMatch(i -> i == ' '));
  }

  /** Test method for {@link RadixKeyStore#toString()}. */
  @Test
  public void testToString() throws IOException, KeyStoreException {
    File file = newFile(TEST_KS_FILENAME);
    try (RadixKeyStore ks = RadixKeyStore.fromFile(file, null, true)) {
      assertThat(ks.toString()).contains(file.toString());
      assertThat(ks.toString()).contains(RadixKeyStore.class.getSimpleName());
    }
  }

  /** Test method for ensuring that only secp256k1 keys are allowed. */
  @Test
  public void testInvalidECKey() throws IOException, GeneralSecurityException, KeyStoreException {
    File file = newFile(TEST_KS_FILENAME);

    KeyStore jks = KeyStore.getInstance("pkcs12");
    jks.load(null, TEST_SECRET.toCharArray());

    // Only secp256k1 curve is supported - use a different curve here
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
    keyGen.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());

    KeyPair keyPair = keyGen.generateKeyPair();
    PrivateKey privKey = keyPair.getPrivate();

    Certificate[] chain = {RadixKeyStore.selfSignedCert(keyPair, 1000, "CN=Invalid")};

    KeyStore.PrivateKeyEntry entry = new KeyStore.PrivateKeyEntry(privKey, chain);
    jks.setEntry("test", entry, new KeyStore.PasswordProtection(new char[0]));

    try (RadixKeyStore rks = new RadixKeyStore(file, jks, TEST_SECRET.toCharArray())) {
      assertThatThrownBy(() -> rks.readKeyPair("test", false))
          .isInstanceOf(KeyStoreException.class)
          .hasMessageContaining("Unknown curve")
          .hasNoCause();
    }
  }

  /** Test method for ensuring that only PrivateKeyEntry values are allowed. */
  @Test
  public void testInvalidEntry() throws IOException, GeneralSecurityException {
    KeyStore jks = KeyStore.getInstance("pkcs12");
    jks.load(null, "password".toCharArray());

    SecretKey sk = new PKCS12Key(TEST_SECRET.toCharArray());
    KeyStore.SecretKeyEntry entry = new KeyStore.SecretKeyEntry(sk);

    assertThatThrownBy(
            () -> jks.setEntry("test", entry, new KeyStore.PasswordProtection(new char[0])))
        .isInstanceOf(java.security.KeyStoreException.class)
        .hasMessageContaining("PKCS12 does not support non-PrivateKeys")
        .hasNoCause();
  }

  private static File newFile(String filename) throws IOException {
    File file = new File(filename);
    if (!Files.deleteIfExists(file.toPath())) {
      // In this case we are fine if "file" does not exist and wasn't deleted.
    }
    assertFalse(file.exists());
    return file;
  }
}
