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

import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;
import com.radixdlt.crypto.encryption.ECIES;
import com.radixdlt.crypto.exception.ECIESException;
import com.radixdlt.crypto.encryption.EncryptedPrivateKey;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.EUID;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.math.ec.ECPoint;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

/**
 * Asymmetric EC key pair provider fixed to curve 'secp256k1'.
 */
@SecurityCritical({ SecurityKind.KEY_GENERATION, SecurityKind.SIG_SIGN, SecurityKind.PK_DECRYPT, SecurityKind.PK_ENCRYPT })
public final class ECKeyPair implements Signing<ECDSASignature> {
	public static final int	BYTES = 32;

	private final byte[] privateKey;
	private final ECPublicKey publicKey;

	private ECKeyPair(final byte[] privateKey, final ECPublicKey publicKey) {
		this.privateKey = privateKey;
		this.publicKey = publicKey;
	}

	/**
	 * Generates a new private and public key pair based on randomness.
	 * @return a newly generated private key and it's corresponding {@link ECPublicKey}.
	 */
	public static ECKeyPair generateNew() {
		try {
			ECKeyPairGenerator generator = new ECKeyPairGenerator();
			ECKeyGenerationParameters keygenParams = new ECKeyGenerationParameters(ECKeyUtils.domain(), ECKeyUtils.secureRandom());
			generator.init(keygenParams);
			AsymmetricCipherKeyPair keypair = generator.generateKeyPair();
			ECPrivateKeyParameters privParams = (ECPrivateKeyParameters) keypair.getPrivate();
			ECPublicKeyParameters pubParams = (ECPublicKeyParameters) keypair.getPublic();

			final ECPublicKey publicKey = ECPublicKey.fromEcPoint(pubParams.getQ());
			byte[] privateKeyBytes = ECKeyUtils.adjustArray(privParams.getD().toByteArray(), BYTES);
			ECKeyUtils.validatePrivate(privateKeyBytes);

			return new ECKeyPair(privateKeyBytes, publicKey);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to generate ECKeyPair", e);
		}
	}

	/**
	 * Generates a new, deterministic {@code ECKeyPair} instance by <b>hashing<b/> the
	 * provided seed.
	 *
	 * @param seed The seed to use when deriving the key pair instance, that is hashed (256 bits).
	 * @return A key pair that corresponds to the hash of the provided seed.
	 * @throws IllegalArgumentException if the seed is empty.
	 */
	public static ECKeyPair fromSeed(byte[] seed) {
		Objects.requireNonNull(seed, "Seed must not be null");

		if (seed.length == 0) {
			throw new IllegalArgumentException("Seed must not be empty");
		}

		try {
			return fromPrivateKey(HashUtils.sha256(seed).asBytes());
		} catch (PrivateKeyException | PublicKeyException e) {
			throw new IllegalStateException("Should always be able to create private key from seed", e);
		}
	}

	/**
	 * Restore {@link ECKeyPair} instance from given private key by computing corresponding public key.
	 * @param privateKey byte array which contains private key.
	 * @return A keypair for provided private key
	 * @throws PrivateKeyException if input byte array does not represent a private key
	 * @throws PublicKeyException if public key can't be computed for given private key
	 */
	public static ECKeyPair fromPrivateKey(byte[] privateKey) throws PrivateKeyException, PublicKeyException {
		ECKeyUtils.validatePrivate(privateKey);

		return new ECKeyPair(privateKey,
			ECPublicKey.fromBytes(ECKeyUtils.keyHandler.computePublicKey(privateKey)));
	}

	public EUID euid() {
		return publicKey.euid();
	}

	public byte[] getPrivateKey() {
		return privateKey;
	}

	public ECPublicKey getPublicKey() {
		return publicKey;
	}
	// TODO move this to new class (yet to be created) `ECPrivateKey`.

	@Override
	public ECPoint multiply(ECPoint point) {
		BigInteger scalarFromPrivateKey = new BigInteger(1, privateKey);
		return point.multiply(scalarFromPrivateKey).normalize();
	}

	@Override
	public ECDSASignature sign(byte[] hash) {
		return ECKeyUtils.keyHandler.sign(hash, privateKey, publicKey.getBytes());
	}

	/**
	 * Signs data using the ECPrivateKey resulting in an ECDSA signature.
	 *
	 * @param data The data to sign
	 * @param enforceLowS If signature should enforce low values of signature part `S`, according to
	 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0062.mediawiki#Low_S_values_in_signatures">BIP-62</a>
	 * @param beDeterministic If signing should use randomness or be deterministic according to
	 * <a href="https://tools.ietf.org/html/rfc6979">RFC6979</a>.
	 * @return An ECDSA Signature.
	 */
	public ECDSASignature sign(byte[] data, boolean enforceLowS, boolean beDeterministic) {
		return ECKeyUtils.keyHandler.sign(data, privateKey, publicKey.getBytes(), enforceLowS, beDeterministic);
	}

	@Override
	public boolean canProduceSignatureForScheme(SignatureScheme signatureScheme) {
		return SignatureScheme.ECDSA.equals(signatureScheme);
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (object instanceof ECKeyPair) {
			ECKeyPair other = (ECKeyPair) object;
			// Comparing private keys should be sufficient
			return Arrays.equals(other.getPrivateKey(), getPrivateKey());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(getPrivateKey());
	}

	@Override
	public String toString() {
		// Not going to print the private key here
		return String.format("%s[%s]",
			getClass().getSimpleName(), getPublicKey().toBase64());
	}

	public static ECKeyPair fromFile(File file) throws PrivateKeyException, PublicKeyException {
		try (InputStream inputStream = new FileInputStream(file)) {
			byte[] privateKey = new byte[32];
			int len = inputStream.read(privateKey);
			if (len != 32) {
				throw new IllegalStateException("Private Key file must be 32 bytes in " + file);
			}
			return fromPrivateKey(privateKey);
		} catch (FileNotFoundException e) {
			throw new IllegalArgumentException("Failed to read file", e);
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to read 32 bytes from content of file", e);
		}
	}

	public byte[] encrypt(byte[] data) {
		try {
			return publicKey.encrypt(data);
		} catch (ECIESException e) {
			throw new IllegalStateException("Failed to encrypt data", e);
		}
	}

	public byte[] decrypt(byte[] data) throws ECIESException {
		return ECIES.decrypt(data, this);
	}


	public byte[] decrypt(byte[] data, EncryptedPrivateKey sharedKey)
			throws PrivateKeyException, PublicKeyException, ECIESException {
		byte[] privateKeyData = decrypt(sharedKey.toByteArray());
		ECKeyPair sharedKeyPair = fromPrivateKey(privateKeyData);
		return sharedKeyPair.decrypt(data);
	}

	public EncryptedPrivateKey encryptPrivateKeyWithPublicKey(ECPublicKey publicKeyUsedToEncrypt) {
		try {
			return new EncryptedPrivateKey(publicKeyUsedToEncrypt.encrypt(privateKey));
		} catch (ECIESException e) {
			throw new IllegalStateException("Failed to encrypt `privateKey` with provided `ECPublicKey`", e);
		}
	}
}
