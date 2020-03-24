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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.crypto.encryption.ECIES;
import com.radixdlt.crypto.encryption.ECIESException;
import com.radixdlt.crypto.encryption.EncryptedPrivateKey;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.Bytes;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.math.ec.ECPoint;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/**
 * Asymmetric EC key pair provider fixed to curve 'secp256k1'.
 */
@SerializerId2("crypto.ec_key_pair")
public final class ECKeyPair implements Signing<ECDSASignature> {
	public static final int	BYTES = 32;

	@JsonProperty("private")
	@DsonOutput(DsonOutput.Output.PERSIST)
	private final byte[] privateKey;

	@JsonProperty("public")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ECPublicKey publicKey;

	private ECKeyPair() {
		this(ECKeyUtils.secureRandom());
	}

	public ECKeyPair(SecureRandom random) {
		try {
			ECKeyPairGenerator generator = new ECKeyPairGenerator();
	        ECKeyGenerationParameters keygenParams = new ECKeyGenerationParameters(ECKeyUtils.domain(), random);
	        generator.init(keygenParams);
	        AsymmetricCipherKeyPair keypair = generator.generateKeyPair();
	        ECPrivateKeyParameters privParams = (ECPrivateKeyParameters) keypair.getPrivate();
	        ECPublicKeyParameters pubParams = (ECPublicKeyParameters) keypair.getPublic();

	        byte[] privateKeyBytes = ECKeyUtils.adjustArray(privParams.getD().toByteArray(), BYTES);
			ECKeyUtils.validatePrivate(privateKeyBytes);

	        this.privateKey = privateKeyBytes;

	        this.publicKey = new ECPublicKey(pubParams.getQ().getEncoded(true));
		} catch (Exception e) {
			throw new IllegalStateException("Failed to generate ECKeyPair", e);
		}
	}

	public ECKeyPair(byte[] privateKey) throws CryptoException {
		try {
			ECKeyUtils.validatePrivate(privateKey);
			this.privateKey = privateKey;
			this.publicKey = new ECPublicKey(ECKeyUtils.keyHandler.computePublicKey(privateKey));
		} catch (Exception ex) {
			throw new CryptoException(ex);
		}
	}

	/**
	 * Generates a new private and public key pair based on randomness.
	 * @return a newly generated private key and it's corresponding {@link ECPublicKey}.
	 */
	public static ECKeyPair generateNew() {
		return new ECKeyPair();
	}

	/**
	 * Generates a new, deterministic {@code ECKeyPair} instance by <b>hashing<b/> the
	 * provided seed.
	 *
	 * @param seed The seed to use when deriving the key pair instance, that is hashed (256 bits).
	 * @return A key pair that corresponds to the hash of the provided seed.
	 * @throws IllegalArgumentException if the seed is empty or a null pointer.
	 */
	public static ECKeyPair fromSeed(byte[] seed) {
		Objects.requireNonNull(seed, "Seed must not be null");

		if (seed.length == 0) {
			throw new IllegalArgumentException("Seed must not be empty");
		}

		byte[] privateKey = Hash.hash256(seed);

		try {
			return new ECKeyPair(privateKey);
		} catch (CryptoException e) {
			throw new IllegalStateException("Should always be able to create private key from seed", e);
		}
	}

	public EUID euid() {
		return this.publicKey.euid();
	}

	public byte[] getPrivateKey() {
		return this.privateKey;
	}

	public ECPublicKey getPublicKey() {
		return this.publicKey;
	}

	// TODO move this to new class (yet to be created) `ECPrivateKey`.
	@Override
	public ECPoint multiply(ECPoint point) {
		BigInteger scalarFromPrivateKey = new BigInteger(1, this.privateKey);
		return point.multiply(scalarFromPrivateKey).normalize();
	}

	@Override
	public ECDSASignature sign(byte[] hash) {
		try {
			return ECKeyUtils.keyHandler.sign(hash, this.privateKey);
		} catch (CryptoException e) {
			throw new IllegalStateException("Failed to sign hash", e);
		}
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
		try {
			return ECKeyUtils.keyHandler.sign(data, this.privateKey, enforceLowS, beDeterministic);
		} catch (CryptoException e) {
			throw new IllegalStateException("Failed to sign hash", e);
		}
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
			return Arrays.equals(other.getPrivateKey(), this.getPrivateKey());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.getPrivateKey());
	}

	@Override
	public String toString() {
		// Not going to print the private key here
		return String.format("%s[%s]",
			getClass().getSimpleName(), Bytes.toBase64String(getPublicKey().getBytes()));
	}

	// ###  From Client Library  ###

	@JsonProperty("version")
	@DsonOutput(DsonOutput.Output.ALL)
	private short version = 100;

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	public ECKeyPair(byte[] publicKey, byte[] privateKey) {
		try {
			this.publicKey = new ECPublicKey(publicKey);
		} catch (CryptoException e) {
			throw new IllegalArgumentException("Failed to create public key from bytes", e);
		}
		this.privateKey = Arrays.copyOf(privateKey, privateKey.length);
	}

	public static ECKeyPair fromFile(File file) throws CryptoException {
		InputStream inputStream;
		try {
			inputStream = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			throw new IllegalArgumentException("Failed to read file", e);
		}

		BufferedInputStream io = new BufferedInputStream(inputStream);

		byte[] privateKey = new byte[32];
		int len = 0;
		try {
			len = io.read(privateKey, 0, 32);
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to read 32 bytes from content of file", e);
		}

		if (len < 32) {
			throw new IllegalStateException("Private Key file must be 32 bytes");
		}

		try {
			return new ECKeyPair(privateKey);
		} catch (Exception e) {
			throw new CryptoException("Failed to create KeyPair from file", e);
		}
	}

	public byte[] encrypt(byte[] data) {
		try {
			return this.publicKey.encrypt(data);
		} catch (ECIESException e) {
			throw new IllegalStateException("Failed to encrypt data", e);
		}
	}

	public byte[] decrypt(byte[] data) throws ECIESException {
		return ECIES.decrypt(data, this);
	}


	public byte[] decrypt(byte[] data, EncryptedPrivateKey sharedKey) throws CryptoException {
		byte[] privateKeyData = this.decrypt(sharedKey.toByteArray());
		ECKeyPair sharedKeyPair = new ECKeyPair(privateKeyData);
		return sharedKeyPair.decrypt(data);
	}

	public EncryptedPrivateKey encryptPrivateKeyWithPublicKey(ECPublicKey publicKeyUsedToEncrypt) {
		byte[] encryptedPrivateKeyBytes = new byte[0];
		try {
			encryptedPrivateKeyBytes = publicKeyUsedToEncrypt.encrypt(this.privateKey);
		} catch (ECIESException e) {
			throw new IllegalStateException("Failed to encrypt `this.privateKey` with provided `ECPublicKey`", e);
		}
		return new EncryptedPrivateKey(encryptedPrivateKeyBytes);
	}

}
