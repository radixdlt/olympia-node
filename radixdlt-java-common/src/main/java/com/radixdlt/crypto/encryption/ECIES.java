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

package com.radixdlt.crypto.encryption;

import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECMultiplicationScalar;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.exception.MacMismatchException;
import com.radixdlt.crypto.exception.ECIESException;
import org.bouncycastle.jcajce.provider.digest.SHA256;
import org.bouncycastle.math.ec.ECPoint;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Encrypt and Decrypt data using ECIES
 * (Elliptic Curve <a href="https://en.wikipedia.org/wiki/Integrated_Encryption_Scheme">Integrated Encryption Scheme</a>, subset of DHIES):
 */
@SecurityCritical({ SecurityKind.PK_DECRYPT, SecurityKind.PK_ENCRYPT })
public final class ECIES {

	private static SecureRandom secureRandom;

	static {
		install();
	}

	static synchronized void install() {
		secureRandom = new SecureRandom();
	}

	private ECIES() {
		throw new IllegalStateException("Can't construct");
	}

	public static <M extends ECMultiplicationScalar> byte[] decrypt(byte[] data, M multiplicationScalar) throws ECIESException {
		try {
			DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(data));

			// 1. Read the `IV` (as in `initialization vector`)
			byte[] iv = new byte[16];
			inputStream.readFully(iv);

			// 2. Read the ephemeral public key
			int publicKeySize = inputStream.readUnsignedByte();
			byte[] publicKeyRaw = new byte[publicKeySize];
			inputStream.readFully(publicKeyRaw);
			ECPublicKey ephemeralPublicKey = ECPublicKey.fromBytes(publicKeyRaw);

			// 3. Do an EC point multiply with this.getPrivateKey() and ephemeral public key. This gives you a point M.
			ECPoint m = multiplicationScalar.multiply(ephemeralPublicKey.getPublicPoint());

			// 4. Use the X component of point M and calculate the Hash H.
			byte[] h = hash(m.getXCoord().getEncoded());

			// 5. The first 32 bytes of H are called key_e and the last 32 bytes are called key_m.
			byte[] keyE = Arrays.copyOfRange(h, 0, 32);
			byte[] keyM = Arrays.copyOfRange(h, 32, 64);

			// 6. Read encrypted data
			byte[] encrypted = new byte[inputStream.readInt()];
			inputStream.readFully(encrypted);

			// 6. Read MAC
			byte[] mac = new byte[32];
			inputStream.readFully(mac);

			// 7. Compare MAC with MAC'. If not equal, decryption will fail.
			byte[] pkMac = calculateMAC(keyM, iv, ephemeralPublicKey, encrypted);
			if (!Arrays.equals(mac, pkMac)) {
				throw new MacMismatchException(pkMac, mac);
			}

			// 8. Decrypt the cipher text with AES-256-CBC, using IV as initialization vector, key_e as decryption key
			//    and the cipher text as payload. The output is the padded input text.
			return Crypt.decrypt(iv, encrypted, keyE);
		} catch (Exception e) {
			throw new ECIESException("Failed to decrypt", e);
		}
	}

	public static final class ECAddDiffieHellmanKDF implements SymmetricKeyDerivationFunction {

		public ECAddDiffieHellmanKDF() {}

		public <S extends ECMultiplicationScalar> SecretKey derive(
			ECPoint ephemeralPublicKeyPoint,
			S blackPrivateKey,
			ECPoint whitePublicKeyPoint
		) {
			var E = ephemeralPublicKeyPoint;
			var a = blackPrivateKey;
			var B = whitePublicKeyPoint;
			var aB =  a.multiply(B).normalize();
			var S = aB.add(E).normalize();
			var x = S.getAffineXCoord();

			var keyData = HashUtils.sha256(x.getEncoded()).asBytes();

			return new SecretKeySpec(keyData, 0, keyData.length, "AES");
		}
	}

	public static final class SealedBox {
		byte[] nonce;
		ECPublicKey ephemeralPublicKey;
		byte [] tag;
		byte[] ciphertext;

		SealedBox(
			byte[] nonce,
			ECPublicKey ephemeralPublicKey,
			byte [] tag,
			byte[] ciphertext
		) {
			this.nonce = nonce;
			this.ephemeralPublicKey = ephemeralPublicKey;
			this.tag = tag;
			this.ciphertext = ciphertext;
		}

		static final int pubKeyByteCount = 33;

		public static SealedBox fromBytes(byte[] combined) {
			// Sanity check length
			int combinedByteCountExcludingCipherText = AESGCM.tagByteCount + AESGCM.nonceByteCount + pubKeyByteCount;
			if (combined.length < combinedByteCountExcludingCipherText) {
				throw new RuntimeException("bad length of combined");
			}

			try {
				DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(combined));

				// 1. Read the `nonce`
				byte[] nonce = new byte[AESGCM.nonceByteCount];
				inputStream.readFully(nonce);

				// 2. Read the ephemeral public key
				byte[] publicKeyRaw = new byte[pubKeyByteCount];
				inputStream.readFully(publicKeyRaw);
				ECPublicKey ephemeralPublicKey = ECPublicKey.fromBytes(publicKeyRaw);

				// 3. Read tag
				byte[] tag = new byte[AESGCM.tagByteCount];
				inputStream.readFully(tag);

				// 4. Read ciphertext
				var cipherLength = combined.length - combinedByteCountExcludingCipherText;
				byte[] ciphertext = new byte[cipherLength];
				inputStream.readFully(ciphertext);

				return new SealedBox(
					nonce,
					ephemeralPublicKey,
					tag,
					ciphertext
				);
			} catch (Exception e) {
				throw new RuntimeException("Failed to decode Sealedbox", e);
			}

//			// Parse "nonce"
//			var nonceData = Arrays.copyOfRange(combined, 0, AESGCM.nonceByteCount);
//			assert(nonceData.length == AESGCM.nonceByteCount);
//
//			// Parse "ephemeral public key"
//			var ephemeralPubKeyBytes = Arrays.copyOfRange(combined, AESGCM.nonceByteCount, AESGCM.nonceByteCount + pubKeyByteCount);
//			assert(ephemeralPubKeyBytes.length == pubKeyByteCount);

		}

		/// The combined representation (nonce || ephemeralPublicKeyCompressed || tag || ciphertext)
		byte[] combined() {
			try {
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
				outputStream.write(nonce);
				assert(ephemeralPublicKey.getBytes().length == pubKeyByteCount);
				outputStream.write(ephemeralPublicKey.getBytes());
				outputStream.write(tag);
				outputStream.write(ciphertext);
				return outputStream.toByteArray( );
			} catch (Exception e) {
				throw new RuntimeException("Failed to combine bytes", e);
			}
		}
	}

	static final class AESGCM {

		static final class AESSealedBox {
			byte[] nonce;
			byte [] tag;
			byte[] ciphertext;

			AESSealedBox(
				byte[] nonce,
				byte [] tag,
				byte[] ciphertext
			) {
				this.nonce = nonce;
				this.tag = tag;
				this.ciphertext = ciphertext;
			}

			/// The combined representation (ciphertext || tag)
			private byte[] cipherAndTag() {
				try {
					ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
					outputStream.write(ciphertext);
					outputStream.write(tag);
					return outputStream.toByteArray( );
				} catch (Exception e) {
					throw new RuntimeException("Failed to combine cipher and tag", e);
				}
			}
		}

		static final int tagByteCount = 16;
		static final int nonceByteCount = 12;

		public static AESSealedBox aesGCMSeal(
			byte[] plaintext,
			SecretKey symmetricKey,
			byte[] nonce,
			byte[] authenticationData
		) {
			assert(nonce.length == nonceByteCount);

			try {
				Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
				GCMParameterSpec spec = new GCMParameterSpec(tagByteCount * 8, nonce);
				cipher.init(Cipher.ENCRYPT_MODE, symmetricKey, spec);
				cipher.updateAAD(authenticationData);
				byte[] ciphertextAndTag = cipher.doFinal(plaintext);

				var ciphertext = Arrays.copyOfRange(ciphertextAndTag, 0, ciphertextAndTag.length - tagByteCount);
				var tag = Arrays.copyOfRange(ciphertextAndTag, ciphertextAndTag.length - tagByteCount, ciphertextAndTag.length);

				return new AESSealedBox(nonce, tag, ciphertext);
			} catch (Exception error) {
				throw new RuntimeException("Failed to get cipher");
			}
		}

		public static byte[] aesGCMOpen(
			AESSealedBox sealedBox,
			SecretKey symmetricKey,
			byte[] authenticationData
		) {
			try {
				Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
				GCMParameterSpec spec = new GCMParameterSpec(tagByteCount * 8, sealedBox.nonce);
				cipher.init(Cipher.DECRYPT_MODE, symmetricKey, spec);
				cipher.updateAAD(authenticationData);
				return cipher.doFinal(sealedBox.cipherAndTag());
			} catch (Exception error) {
				throw new RuntimeException("Failed to get cipher");
			}
		}
	}

	public static <S extends ECMultiplicationScalar, KDF extends SymmetricKeyDerivationFunction> SealedBox seal(
		KDF symmetricKDF,
		byte[] plaintext,
		ECPublicKey whitePublicKey,
		S blackPrivateKey
	) throws ECIESException {
		var nonce = new byte[12];
		secureRandom.nextBytes(nonce);

		var ephemeralKeyPair = ECKeyPair.generateNew();
		var ephemeralPublicKey = ephemeralKeyPair.getPublicKey();

		var symmetricKey = symmetricKDF.derive(
			ephemeralPublicKey,
			blackPrivateKey,
			whitePublicKey
		);

		var symmetricSealedBox = AESGCM.aesGCMSeal(
			plaintext,
			symmetricKey,
			nonce,
			ephemeralPublicKey.getBytes()
		);

		return new SealedBox(
			symmetricSealedBox.nonce,
			ephemeralPublicKey,
			symmetricSealedBox.tag,
			symmetricSealedBox.ciphertext
		);
	}

	public static <S extends ECMultiplicationScalar, KDF extends SymmetricKeyDerivationFunction> byte[] open(
		KDF symmetricKDF,
		SealedBox sealedBox,
		ECPublicKey whitePublicKey,
		S blackPrivateKey
	) throws ECIESException {
		var ephemeralPublicKey = sealedBox.ephemeralPublicKey;

		var symmetricKey = symmetricKDF.derive(
			ephemeralPublicKey,
			blackPrivateKey,
			whitePublicKey
		);

		return AESGCM.aesGCMOpen(
			new AESGCM.AESSealedBox(
				sealedBox.nonce,
				sealedBox.tag,
				sealedBox.ciphertext
			),
			symmetricKey,
			ephemeralPublicKey.getBytes()
		);
	}

	public static byte[] encrypt(byte[] data, ECPublicKey publicKey) throws ECIESException {
		byte[] iv = new byte[16];
		secureRandom.nextBytes(iv);
		return encrypt(data, publicKey.getPublicPoint(), ECKeyPair.generateNew(), iv);
	}

	@VisibleForTesting
	static byte[] encrypt(byte[] data, ECPoint publicKeyPointOnCurve, ECKeyPair ephemeral, byte[] iv) throws ECIESException {
		try {
			// 1. The destination is this.getPublicKey()
			// 2. Generate 16 random bytes using a secure random number generator.
			// Call them `IV` (as in `initialization vector`)
			// Use supplied IV so we can test versus deterministic test vectors.

			// 3. Generate a new ephemeral EC key pair
			// Use the supplied "ephemeral" key so we can test deterministic test vectors.

			// 4. Do an EC point multiply with `publicKeyPointOnCurve` and ephemeral private key. This gives you a point M.
			ECPoint m = ephemeral.multiply(publicKeyPointOnCurve);

			// 5. Use the X component of point M and calculate the Hash H.
			byte[] h = hash(m.getXCoord().getEncoded());

			// 6. The first 32 bytes of H are called key_e and the last 32 bytes are called key_m.
			byte[] keyE = Arrays.copyOfRange(h, 0, 32);
			byte[] keyM = Arrays.copyOfRange(h, 32, 64);

			// 7. Pad the input text to a multiple of 16 bytes, in accordance to PKCS7.
			// 8. Encrypt the data with AES-256-CBC, using IV as initialization vector,
			// key_e as encryption key and the padded input text as payload. Call the output cipher text.
			byte[] encrypted = Crypt.encrypt(iv, data, keyE);

			// 9. Calculate a 32 byte MAC with HMACSHA256, using key_m as salt and
			// IV + ephemeral.pub + cipher text as data. Call the output MAC.
			byte[] mac = calculateMAC(keyM, iv, ephemeral.getPublicKey(), encrypted);

			// 10. Write out the encryption result IV + ephemeral.pub + encrypted + MAC
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream outputStream = new DataOutputStream(baos);
			outputStream.write(iv);
			outputStream.writeByte(ephemeral.getPublicKey().length());
			outputStream.write(ephemeral.getPublicKey().getBytes());
			outputStream.writeInt(encrypted.length);
			outputStream.write(encrypted);
			outputStream.write(mac);

			return baos.toByteArray();
		} catch (Exception e) {
			throw new ECIESException("Failed to encrypt", e);
		}
	}

	private static byte[] calculateMAC(byte[] salt, byte[] iv, ECPublicKey publicKey, byte[] cipherText) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream outputStream = new DataOutputStream(baos);

		outputStream.write(iv);
		outputStream.write(publicKey.getBytes());
		outputStream.write(cipherText);

		try {
			Mac mac = Mac.getInstance("HmacSHA256", "BC");
			mac.init(new SecretKeySpec(salt, "HmacSHA256"));
			return mac.doFinal(baos.toByteArray());
		} catch (GeneralSecurityException e) {
			throw new IOException(e);
		}
	}

	private static byte[] hash(byte[] data) {
		return HashUtils.sha512(data).asBytes();
	}
}
