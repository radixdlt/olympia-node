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

import com.radixdlt.common.EUID;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.WireIO;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Asymmetric EC key pair provider fixed to curve 'secp256k1'.
 */
public final class ECKeyPair implements Signing<ECDSASignature> {
	public static final int	BYTES = 32;

	private final byte[] privateKey;
	private final ECPublicKey publicKey;

	public ECKeyPair() throws CryptoException {
		this(ECKeyUtils.secureRandom);
	}

	public ECKeyPair(SecureRandom random) throws CryptoException {
		try {
			ECKeyPairGenerator generator = new ECKeyPairGenerator();
	        ECKeyGenerationParameters keygenParams = new ECKeyGenerationParameters(ECKeyUtils.domain, random);
	        generator.init(keygenParams);
	        AsymmetricCipherKeyPair keypair = generator.generateKeyPair();
	        ECPrivateKeyParameters privParams = (ECPrivateKeyParameters) keypair.getPrivate();
	        ECPublicKeyParameters pubParams = (ECPublicKeyParameters) keypair.getPublic();

	        byte[] privateKeyBytes = ECKeyUtils.adjustArray(privParams.getD().toByteArray(), BYTES);
			ECKeyUtils.validatePrivate(privateKeyBytes);

	        this.privateKey = privateKeyBytes;

	        this.publicKey = new ECPublicKey(pubParams.getQ().getEncoded(true));
		} catch (Exception ex) {
			throw new CryptoException(ex);
		}
	}

	public ECKeyPair(byte[] key) throws CryptoException {
		try {
			ECKeyUtils.validatePrivate(key);
			this.privateKey = key;
			this.publicKey = new ECPublicKey(ECKeyUtils.keyHandler.computePublicKey(key));
		} catch (Exception ex) {
			throw new CryptoException(ex);
		}
	}

	public EUID getUID() {
		return this.publicKey.getUID();
	}

	public byte[] getPrivateKey() {
		return this.privateKey;
	}

	public ECPublicKey getPublicKey() {
		return this.publicKey;
	}

//	public ECDSASignature sign(Hash hash) throws CryptoException {
//		return sign(hash.toByteArray());
//	}

	public ECDSASignature sign(byte[] hash) throws CryptoException {
		return ECKeyUtils.keyHandler.sign(hash, this.privateKey);
	}

	@Override
	public SignatureScheme signatureScheme() {
		return SignatureScheme.ECDSA;
	}

	public <U extends Signature> boolean canProduceSignatureOfType(Class<U> signatureType) {
		return signatureType.equals(ECDSASignature.class);
	}

	public byte[] decrypt(byte[] data) throws CryptoException {
		try {
			WireIO.Reader reader = new WireIO.Reader(data);

			// 1. Read the initialization vector, IV
			byte[] iv = reader.readBytes(16);

			// 2. Read the ephemeral public key
			ECPublicKey ephemeral = new ECPublicKey(reader.readBytes(reader.readByte()));

			// 3. Do an EC point multiply with this.getPrivateKey() and ephemeral public key. This gives you a point M.
			ECPoint m = ephemeral.getPublicPoint().multiply(new BigInteger(1, getPrivateKey())).normalize();

			// 4. Use the X component of point M and calculate the SHA512 hash H.
			byte[] h = Hash.hash512(m.getXCoord().getEncoded());

			// 5. The first 32 bytes of H are called key_e and the last 32 bytes are called key_m.
			byte[] keyE = Arrays.copyOfRange(h, 0, 32);
			byte[] keyM = Arrays.copyOfRange(h, 32, 64);

			// 6. Read encrypted data
			byte[] encrypted = reader.readBytes(reader.readInt());

			// 6. Read MAC
			byte[] mac = reader.readBytes(32);

			// 7. Compare MAC with MAC'. If not equal, decryption will fail.
			if (!Arrays.equals(mac, ECKeyUtils.calculateMAC(keyM, iv, ephemeral, encrypted))) {
				throw new CryptoException("MAC mismatch when decrypting");
			}

			// 8. Decrypt the cipher text with AES-256-CBC, using IV as initialization vector, key_e as decryption key
			// and the cipher text as payload. The output is the padded input text.
			return ECKeyUtils.crypt(false, iv, encrypted, keyE);
		} catch (CryptoException e) {
			throw e;
		} catch (Exception e) {
			throw new CryptoException("Failed to decrypt", e);
		}
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
}
