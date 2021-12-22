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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;
import com.radixdlt.crypto.exception.KeyStoreException;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.sec.ECPrivateKey;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * A key store that provide basic integrity checks, plus an optional level of security.
 *
 * <p>Each store can hold multiple names, and keys are identified by their unique name within the
 * store,
 *
 * <p><b>Implementation note:</b><br>
 * This store uses a PKCS#12 representation for the underlying storage, and the store requires a
 * non-empty password to protect it. In order to ease unattended use, note that where a password is
 * required, a {@code null}, or zero length password may be provided, in which case the default 5
 * character password, "radix" is used. Clearly this is insecure, and clients should make an effort
 * to specify passwords in a secure way.
 */
@SecurityCritical(SecurityKind.KEY_STORE)
public final class RadixKeyStore implements Closeable {
  // ASN.1 Object Identifiers for various things we use
  private static final ASN1ObjectIdentifier OID_EC_ENCRYPTION =
      new ASN1ObjectIdentifier("1.2.840.10045.2.1");
  private static final ASN1ObjectIdentifier OID_SECP256K1_CURVE =
      new ASN1ObjectIdentifier("1.3.132.0.10");
  private static final ASN1ObjectIdentifier OID_BASIC_CONSTRAINTS =
      new ASN1ObjectIdentifier("2.5.29.19");

  private static final String CERT_SIGNATURE_ALG = "SHA256withECDSA";
  private static final String DEFAULT_SUBJECT_DN =
      "CN=Radix DLT Network, OU=Network, O=Radix DLT, C=UK";

  // Default key to use for key store when none is provided.
  private static final char[] defaultKey = "radix".toCharArray();

  // PKCS12 has no mechanism for per-key passwords, but the JCE KeyStore requires a password
  // for a PrivateKeyEntry.  We work around this by providing an empty password.
  private static KeyStore.PasswordProtection emptyPassword =
      new KeyStore.PasswordProtection(new char[0]);

  // Unexpectedly, the BouncyCastleProvider.getPrivateKey(...) and getPublicKey(...) static methods
  // require the BouncyCastleProvider() constructor to be called at least once, otherwise they fail.
  // This looks like an implementation issue with Bouncy Castle (static data initialisation in
  // constructor), but we work around it here by creating a dummy provider and then discarding it.
  // This is as at BC 1.64.
  static {
    @SuppressWarnings("unused")
    Object unused = new BouncyCastleProvider();
  }

  /**
   * Load a private key from file, and compute the public key.
   *
   * <p>Note that if {@code create} is set to {@code true}, then the file will be created if it does
   * not exist. If the file is created, then it's permissions will be set to just {@link
   * PosixFilePermission#OWNER_READ} and {@link PosixFilePermission#OWNER_WRITE} on Posix
   * filesystems.
   *
   * @param file The file to load the private key from
   * @param storePassword The password to use for securing the store. Set to {@code null} if a
   *     default password should be used. Note: using {@code null} effectively means there is
   *     <b><i>no security</i></b> on the underlying key store.
   * @param create Set to {@code true} if the file should be created if it doesn't exist.
   * @return A {@link RadixKeyStore}
   * @throws IOException If reading or writing the file fails
   * @throws KeyStoreException If the key read from the file is invalid
   */
  public static RadixKeyStore fromFile(File file, char[] storePassword, boolean create)
      throws IOException, KeyStoreException {
    try {
      var ks = KeyStore.getInstance("pkcs12");
      var usedStorePassword =
          (storePassword == null || storePassword.length == 0) ? defaultKey : storePassword;
      initializeKeyStore(ks, file, usedStorePassword, create);
      return new RadixKeyStore(file, ks, usedStorePassword.clone());
    } catch (GeneralSecurityException ex) {
      throw new KeyStoreException("Can't load key store", ex);
    }
  }

  private final File file;
  private final KeyStore keyStore;
  private final char[] storePassword;

  @VisibleForTesting
  RadixKeyStore(File file, KeyStore keyStore, char[] storePassword) {
    this.file = Objects.requireNonNull(file);
    this.keyStore = Objects.requireNonNull(keyStore);
    this.storePassword = Objects.requireNonNull(storePassword);
  }

  @VisibleForTesting
  char[] storePassword() {
    return storePassword;
  }

  /**
   * Reads a key pair with the specified name from the key store.
   *
   * @param name The name of the key in the key store. If no key with the given name is present, it
   *     will be created if specified, otherwise a {@link KeyStoreException} will be raised.
   * @param create Set to {@code true} to create a new random key if the specified key does not
   *     exist in the store.
   * @return The {@link ECKeyPair} read from the key store
   * @throws KeyStoreException If an error occurs while reading the key
   * @throws PrivateKeyException If an error occurs while validating the private key
   * @throws PublicKeyException If an error occurs while computing the public key
   */
  public ECKeyPair readKeyPair(String name, boolean create)
      throws KeyStoreException, PrivateKeyException, PublicKeyException {
    try {
      var entry = this.keyStore.getEntry(name, emptyPassword);
      if (entry == null) {
        if (create) {
          var newKeyPair = ECKeyPair.generateNew();
          writeKeyPair(name, newKeyPair);
          return newKeyPair;
        } else {
          throw new KeyStoreException("No such entry: " + name);
        }
      } else {
        return processEntry(name, entry);
      }
    } catch (GeneralSecurityException | IOException e) {
      throw new KeyStoreException("Key store error while reading key", e);
    }
  }

  /**
   * Writes the specified key pair identified by the specified name to the key store.
   *
   * <p><b>Implementation note:</b><br>
   * Note that this method will create a new file and set it's permissions to just {@link
   * PosixFilePermission#OWNER_READ} and {@link PosixFilePermission#OWNER_WRITE} on Posix
   * filesystems.
   *
   * @param name The name the key will have in the key store. If a key already exists with this
   *     name, it will be overwritten.
   * @param ecKeyPair The {@link ECKeyPair} to write to the store.
   * @throws KeyStoreException If an error occurs writing the key
   * @throws IOException If an error occurs persisting the key store
   */
  public void writeKeyPair(String name, ECKeyPair ecKeyPair) throws KeyStoreException, IOException {
    byte[] encodedPrivKey = null;
    try {
      encodedPrivKey = encodePrivKey(ecKeyPair.getPrivateKey());
      var pki = PrivateKeyInfo.getInstance(encodedPrivKey);
      // Note that while PrivateKey objects are in theory destroyable,
      // the BCEC implementation has not yet been updated to accommodate.
      var privateKey = BouncyCastleProvider.getPrivateKey(pki);

      var encodedPubKey = encodePubKey(ecKeyPair.getPublicKey().getBytes());
      var spki = SubjectPublicKeyInfo.getInstance(encodedPubKey);
      var publicKey = BouncyCastleProvider.getPublicKey(spki);

      var keyPair = new KeyPair(publicKey, privateKey);

      Certificate[] chain = {selfSignedCert(keyPair, 1000, DEFAULT_SUBJECT_DN)};
      KeyStore.PrivateKeyEntry pke = new KeyStore.PrivateKeyEntry(privateKey, chain);
      this.keyStore.setEntry(name, pke, emptyPassword);
      writeKeyStore(this.file, this.keyStore, this.storePassword);
    } catch (GeneralSecurityException ex) {
      throw new KeyStoreException("Error while writing key", ex);
    } finally {
      if (encodedPrivKey != null) {
        Arrays.fill(encodedPrivKey, (byte) 0);
      }
    }
  }

  @Override
  public void close() throws IOException {
    Arrays.fill(this.storePassword, ' ');
  }

  @Override
  public String toString() {
    return String.format("%s[%s]", getClass().getSimpleName(), file.toString());
  }

  private ECKeyPair processEntry(String name, Entry entry)
      throws KeyStoreException, PrivateKeyException, IOException, PublicKeyException {

    // Should not be possible to have a non-private key entry in a PKCS#12 store.
    // Still do the check here anyway.
    if (entry instanceof KeyStore.PrivateKeyEntry) {
      KeyStore.PrivateKeyEntry pkEntry = (KeyStore.PrivateKeyEntry) entry;
      // Note that while PrivateKey objects are in theory destroyable,
      // the BCEC implementation has not yet been updated to accommodate.
      PrivateKey pk = pkEntry.getPrivateKey();
      byte[] encoded = pk.getEncoded();
      try {
        return ECKeyPair.fromPrivateKey(decodePrivKey(encoded));
      } finally {
        Arrays.fill(encoded, (byte) 0);
      }
    } else {
      throw new KeyStoreException(
          String.format("Entry %s is not a private key: %s", name, entry.getClass()));
    }
  }

  private static void initializeKeyStore(
      KeyStore ks, File file, char[] storePassword, boolean create)
      throws GeneralSecurityException, IOException {
    try (var is = new FileInputStream(file)) {
      ks.load(is, storePassword);
    } catch (FileNotFoundException ex) {
      if (create) {
        // Create a new keystore
        ks.load(null, storePassword);
        writeKeyStore(file, ks, storePassword);
      } else {
        throw ex;
      }
    }
  }

  /**
   * Creates a self-signed certificate from the specified key-pair.
   *
   * <p>The validity period for the certificate extends from the current local clock time for the
   * specified validity period in years.
   *
   * @param keyPair The key-pair to create the self-signed certificate for.
   * @param validityYears Years The validity period in years.
   * @param subjectDN The key's distinguished subject name.
   * @return A self-signed certificate
   * @throws KeyStoreException If an error occurs while building the certificate
   * @throws IOException If an I/O error occurs while building the certificate
   */
  @VisibleForTesting
  static Certificate selfSignedCert(KeyPair keyPair, int validityYears, String subjectDN)
      throws KeyStoreException, IOException {
    X500Name dnName = new X500Name(subjectDN);
    LocalDateTime startDate = LocalDateTime.now();
    LocalDateTime endDate = startDate.plusYears(validityYears);
    BigInteger certSerialNumber = BigInteger.valueOf(toDate(startDate).getTime());

    var certBuilder =
        new JcaX509v3CertificateBuilder(
            dnName,
            certSerialNumber,
            toDate(startDate),
            toDate(endDate),
            dnName,
            keyPair.getPublic());
    BasicConstraints basicConstraints = new BasicConstraints(true);
    certBuilder.addExtension(OID_BASIC_CONSTRAINTS, true, basicConstraints);

    try {
      ContentSigner contentSigner =
          new JcaContentSignerBuilder(CERT_SIGNATURE_ALG).build(keyPair.getPrivate());
      return new JcaX509CertificateConverter().getCertificate(certBuilder.build(contentSigner));
    } catch (OperatorCreationException | CertificateException ex) {
      throw new KeyStoreException("Error creating certificate", ex);
    }
  }

  private static Date toDate(LocalDateTime dateToConvert) {
    return Date.from(dateToConvert.atZone(ZoneOffset.UTC).toInstant());
  }

  /**
   * Decodes the specified ASN.1 encoded <a
   * href="https://tools.ietf.org/html/rfc5208#section-5">Private Key Info</a> structure into a raw
   * 256-bit secp256k1 private key.
   *
   * @param encoded The encoded private key
   * @return The raw {@link ECKeyPair#BYTES} length private key.
   * @throws IOException If an error occurred decoding the ASN.1 key.
   * @throws KeyStoreException If the decoded key is not for the secp256k1 curve.
   */
  private static byte[] decodePrivKey(byte[] encoded) throws IOException, KeyStoreException {
    PrivateKeyInfo pki = PrivateKeyInfo.getInstance(encoded);
    ECPrivateKey ecpk = ECPrivateKey.getInstance(pki.parsePrivateKey());
    ASN1Primitive params = ecpk.getParameters();
    if (!OID_SECP256K1_CURVE.equals(params)) {
      throw new KeyStoreException("Unknown curve: " + params.toString());
    }
    return ECKeyUtils.adjustArray(ecpk.getKey().toByteArray(), ECKeyPair.BYTES);
  }

  /**
   * Encodes the specified raw secp256k1 private key into an ASN.1 encoded <a
   * href="https://tools.ietf.org/html/rfc5208#section-5">Private Key Info</a> structure.
   *
   * <p>Note that the encoded key will not include a public key.
   *
   * @param rawkey The raw secp256k1 private key.
   * @return The ASN.1 encoded private key.
   * @throws IOException If an error occurs encoding the ASN.1 key.
   */
  private static byte[] encodePrivKey(byte[] rawkey) throws IOException {
    AlgorithmIdentifier ecSecp256k1 =
        new AlgorithmIdentifier(OID_EC_ENCRYPTION, OID_SECP256K1_CURVE);
    BigInteger key = new BigInteger(1, rawkey);
    ECPrivateKey privateKey =
        new ECPrivateKey(ECKeyPair.BYTES * Byte.SIZE, key, OID_SECP256K1_CURVE);
    PrivateKeyInfo pki = new PrivateKeyInfo(ecSecp256k1, privateKey);
    return pki.getEncoded();
  }

  private static byte[] encodePubKey(byte[] rawkey) throws IOException {
    AlgorithmIdentifier ecSecp256k1 =
        new AlgorithmIdentifier(OID_EC_ENCRYPTION, OID_SECP256K1_CURVE);
    SubjectPublicKeyInfo spki = new SubjectPublicKeyInfo(ecSecp256k1, rawkey);
    return spki.getEncoded();
  }

  private static void writeKeyStore(File file, KeyStore ks, char[] storePassword)
      throws GeneralSecurityException, IOException {
    try (OutputStream os = new FileOutputStream(file)) {
      try {
        // Make some effort to make file read/writable only by owner
        Set<PosixFilePermission> perms =
            ImmutableSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        Files.setPosixFilePermissions(file.toPath(), perms);
      } catch (UnsupportedOperationException ignoredException) {
        // probably windows
      }
      ks.store(os, storePassword);
    }
  }
}
