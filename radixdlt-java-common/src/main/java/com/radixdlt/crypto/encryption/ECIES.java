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
import org.bouncycastle.math.ec.ECPoint;

import javax.crypto.Mac;
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
			ECPoint m = multiplicationScalar.multiply(ephemeralPublicKey.getEcPoint());

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

	public static byte[] encrypt(byte[] data, ECPublicKey publicKey) throws ECIESException {
		byte[] iv = new byte[16];
		secureRandom.nextBytes(iv);
		return encrypt(data, publicKey.getEcPoint(), ECKeyPair.generateNew(), iv);
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
			final var ephemeralBytes = ephemeral.getPublicKey().getCompressedBytes();
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream outputStream = new DataOutputStream(baos);
			outputStream.write(iv);
			outputStream.writeByte(ephemeralBytes.length);
			outputStream.write(ephemeralBytes);
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
		outputStream.write(publicKey.getCompressedBytes());
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
