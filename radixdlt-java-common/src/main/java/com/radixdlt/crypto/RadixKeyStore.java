/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.crypto;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.Set;

import com.radixdlt.crypto.exception.KeyStoreException;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;

/**
 * A key store that provide basic integrity checks, plus an optional level
 * of security.
 * <p>
 * Each store can hold multiple names, and keys are identified by their
 * unique name within the store,
 * <p>
 * <b>Implementation note:</b><br>
 * This store uses a PKCS#12 representation for the underlying storage, and
 * the store requires a non-empty password to protect it.
 * In order to ease unattended use, note that where a password is required,
 * a {@code null}, or zero length password may be provided, in which case the
 * default 5 character password, "radix" is used.  Clearly this is insecure,
 * and clients should make an effort to specify passwords in a secure way.
 */
@SecurityCritical(SecurityKind.KEY_STORE)
public final class RadixKeyStore implements Closeable {
	// ASN.1 Object Identifiers for various things we use
	private static final ASN1ObjectIdentifier OID_EC_ENCRYPTION     = new ASN1ObjectIdentifier("1.2.840.10045.2.1");
	private static final ASN1ObjectIdentifier OID_SECP256K1_CURVE   = new ASN1ObjectIdentifier("1.3.132.0.10");
	private static final ASN1ObjectIdentifier OID_BASIC_CONSTRAINTS = new ASN1ObjectIdentifier("2.5.29.19");

	private static final String CERT_SIGNATURE_ALG = "SHA256withECDSA";
	private static final String DEFAULT_SUBJECT_DN = "CN=Radix DLT Network, OU=Network, O=Radix DLT, C=UK";

	// Default key to use for key store when none is provided.
	private static final char[] defaultKey = "radix".toCharArray();

	// PKCS12 has no mechanism for per-key passwords, but the JCE KeyStore requires a password
	// for a PrivateKeyEntry.  We work around this by providing an empty password.
	private static KeyStore.PasswordProtection emptyPassword = new KeyStore.PasswordProtection(new char[0]);

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
	 * <p>
	 * Note that if {@code create} is set to {@code true}, then the file will
	 * be created if it does not exist.  If the file is created, then it's
	 * permissions will be set to just {@link PosixFilePermission#OWNER_READ}
	 * and {@link PosixFilePermission#OWNER_WRITE} on Posix filesystems.
	 *
	 * @param file  The file to load the private key from
	 * @param storePassword The password to use for securing the store.
	 * 		Set to {@code null} if a default password should be used.
	 * 		Note: using {@code null} effectively means there is
	 * 		<b><i>no security</i></b> on the underlying key store.
	 * @param create Set to {@code true} if the file should be created if it doesn't exist.
	 * @return A {@link RadixKeyStore}
	 * @throws IOException If reading or writing the file fails
	 * @throws KeyStoreException If the key read from the file is invalid
	 */
	public static RadixKeyStore fromFile(File file, char[] storePassword, boolean create)
			throws IOException, KeyStoreException {
		try {
			final KeyStore ks = KeyStore.getInstance("pkcs12");
			char[] usedStorePassword = (storePassword == null || storePassword.length == 0) ? defaultKey : storePassword;
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

	/**
	 * Reads a key pair with the specified name from the key store.
	 *
	 * @param name The name of the key in the key store.  If no key with the
	 * 		given name is present, it will be created if specified, otherwise
	 * 		a {@link KeyStoreException} will be raised.
	 * @param create Set to {@code true} to create a new random key if the
	 * 		specified key does not exist in the store.
	 * @return The {@link ECKeyPair} read from the key store
	 * @throws KeyStoreException If an error occurs while reading the key
	 * @throws PrivateKeyException If an error occurs while validating the private key
	 * @throws PublicKeyException If an error occurs while computing the public key
	 */
	public ECKeyPair readKeyPair(String name, boolean create)
			throws KeyStoreException, PrivateKeyException, PublicKeyException {
		try {
			KeyStore.Entry entry = this.keyStore.getEntry(name, emptyPassword);
			if (entry == null) {
				if (create) {
					ECKeyPair newKeyPair = ECKeyPair.generateNew();
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
	 * Writes the specified key pair identified by the specified name to the
	 * key store.
	 * <p>
	 * <b>Implementation note:</b><br> Note that this method will create a new
	 * file and set it's permissions to just
	 * {@link PosixFilePermission#OWNER_READ} and
	 * {@link PosixFilePermission#OWNER_WRITE} on Posix filesystems.
	 *
	 * @param name The name the key will have in the key store.  If a key
	 * 		already exists with this name, it will be overwritten.
	 * @param ecKeyPair The {@link ECKeyPair} to write to the store.
	 * @throws KeyStoreException If an error occurs writing the key
	 * @throws IOException If an error occurs persisting the key store
	 */
	public void writeKeyPair(String name, ECKeyPair ecKeyPair) throws KeyStoreException, IOException {
		byte[] encodedPrivKey = null;
		try {
			encodedPrivKey = encodePrivKey(ecKeyPair.getPrivateKey());
			PrivateKeyInfo pki = PrivateKeyInfo.getInstance(encodedPrivKey);
			// Note that while PrivateKey objects are in theory destroyable,
			// the BCEC implementation has not yet been updated to accommodate.
			PrivateKey privateKey = BouncyCastleProvider.getPrivateKey(pki);

			byte[] encodedPubKey = encodePubKey(ecKeyPair.getPublicKey().getBytes());
			SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(encodedPubKey);
			PublicKey publicKey = BouncyCastleProvider.getPublicKey(spki);

			KeyPair keyPair = new KeyPair(publicKey, privateKey);

			Certificate[] chain = {
				selfSignedCert(keyPair, 1000, DEFAULT_SUBJECT_DN)
			};
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
			throw new KeyStoreException(String.format("Entry %s is not a private key: %s", name, entry.getClass()));
		}
	}

	private static void initializeKeyStore(KeyStore ks, File file, char[] storePassword, boolean create)
		throws GeneralSecurityException, IOException {
		try (InputStream is = new FileInputStream(file)) {
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
	 * <p>
	 * The validity period for the certificate extends from the current
	 * local clock time for the specified validity period in years.
	 *
	 * @param keyPair The key-pair to create the self-signed certificate for.
	 * @param validityYears Years The validity period in years.
	 * @param subjectDN The key's distinguished subject name.
	 * @return A self-signed certificate
	 * @throws KeyStoreException If an error occurs while building the certificate
	 * @throws IOException If an I/O error occurs while building the certificate
	 */
	@VisibleForTesting
	static Certificate selfSignedCert(KeyPair keyPair, int validityYears, String subjectDN) throws KeyStoreException, IOException {
		X500Name dnName = new X500Name(subjectDN);
		LocalDateTime startDate = LocalDateTime.now();
		LocalDateTime endDate = startDate.plusYears(validityYears);
		BigInteger certSerialNumber = BigInteger.valueOf(toDate(startDate).getTime());

		JcaX509v3CertificateBuilder certBuilder =
				new JcaX509v3CertificateBuilder(dnName, certSerialNumber, toDate(startDate), toDate(endDate), dnName, keyPair.getPublic());
		BasicConstraints basicConstraints = new BasicConstraints(true);
		certBuilder.addExtension(OID_BASIC_CONSTRAINTS, true, basicConstraints);

		try {
			ContentSigner contentSigner = new JcaContentSignerBuilder(CERT_SIGNATURE_ALG).build(keyPair.getPrivate());
			return new JcaX509CertificateConverter().getCertificate(certBuilder.build(contentSigner));
		} catch (OperatorCreationException | CertificateException ex) {
			throw new KeyStoreException("Error creating certificate", ex);
		}
	}

	private static Date toDate(LocalDateTime dateToConvert) {
		return Date.from(dateToConvert.atZone(ZoneOffset.UTC).toInstant());
	}

	/**
	 * Decodes the specified ASN.1 encoded
	 * <a href="https://tools.ietf.org/html/rfc5208#section-5">Private Key Info</a>
	 * structure into a raw 256-bit secp256k1 private key.
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
	 * Encodes the specified raw secp256k1 private key into an ASN.1 encoded
	 * <a href="https://tools.ietf.org/html/rfc5208#section-5">Private Key Info</a>
	 * structure.
	 * <p>
	 * Note that the encoded key will not include a public key.
	 *
	 * @param rawkey The raw secp256k1 private key.
	 * @return The ASN.1 encoded private key.
	 * @throws IOException If an error occurs encoding the ASN.1 key.
	 */
	private static byte[] encodePrivKey(byte[] rawkey) throws IOException {
		AlgorithmIdentifier ecSecp256k1 = new AlgorithmIdentifier(OID_EC_ENCRYPTION, OID_SECP256K1_CURVE);
		BigInteger key = new BigInteger(1, rawkey);
		ECPrivateKey privateKey = new ECPrivateKey(ECKeyPair.BYTES * Byte.SIZE, key, OID_SECP256K1_CURVE);
		PrivateKeyInfo pki = new PrivateKeyInfo(ecSecp256k1, privateKey);
		return pki.getEncoded();
	}

	private static byte[] encodePubKey(byte[] rawkey) throws IOException {
		AlgorithmIdentifier ecSecp256k1 = new AlgorithmIdentifier(OID_EC_ENCRYPTION, OID_SECP256K1_CURVE);
		SubjectPublicKeyInfo spki = new SubjectPublicKeyInfo(ecSecp256k1, rawkey);
		return spki.getEncoded();
	}

	private static void writeKeyStore(File file, KeyStore ks, char[] storePassword) throws GeneralSecurityException, IOException {
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
