/*
 * (C) Copyright 2021 Radix DLT Ltd
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

import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.modes.SICBlockCipher;
import org.bouncycastle.crypto.params.IESWithCipherParameters;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.math.ec.ECPoint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;

public final class ECIESCoder {
	public static final int KEY_SIZE = 128;
	public static final int OVERHEAD_SIZE = 65 + KEY_SIZE / 8 + 32;

	private ECIESCoder() {
	}

	public static byte[] decrypt(BigInteger privKey, byte[] cipher) throws IOException, InvalidCipherTextException {
		return decrypt(privKey, cipher, null);
	}

	public static byte[] decrypt(BigInteger privKey, byte[] cipher, byte[] macData) throws InvalidCipherTextException {
		final var is = new ByteArrayInputStream(cipher);
		try {
			final var ephemBytes = new byte[2 * ((ECKeyUtils.domain().getCurve().getFieldSize() + 7) / 8) + 1];
			is.read(ephemBytes);
			final var ephem = ECKeyUtils.domain().getCurve().decodePoint(ephemBytes);
			final var iv = new byte[KEY_SIZE / 8];
			is.read(iv);
			final var cipherBody = new byte[is.available()];
			is.read(cipherBody);
			return decrypt(ephem, privKey, iv, cipherBody, macData);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static byte[] decrypt(ECPoint ephem, BigInteger prv, byte[] iv, byte[] cipher, byte[] macData) throws InvalidCipherTextException {
		final var iesEngine = new IESEngine(
			new ECDHBasicAgreement(),
			new ConcatKDFBytesGenerator(new SHA256Digest()),
			new HMac(new SHA256Digest()),
			new SHA256Digest(),
			new BufferedBlockCipher(new SICBlockCipher(new AESEngine()))
		);

		final var d = new byte[] {};
		final var e = new byte[] {};

		final var p = new IESWithCipherParameters(d, e, KEY_SIZE, KEY_SIZE);
		final var parametersWithIV = new ParametersWithIV(p, iv);
		iesEngine.init(
			false,
			new ECPrivateKeyParameters(prv, ECKeyUtils.domain()),
			new ECPublicKeyParameters(ephem, ECKeyUtils.domain()),
			parametersWithIV
		);

		return iesEngine.processBlock(cipher, 0, cipher.length, macData);
	}

	public static byte[] encrypt(ECPoint toPub, byte[] plaintext) {
		return encrypt(toPub, plaintext, null);
	}

	public static byte[] encrypt(ECPoint toPub, byte[] plaintext, byte[] macData) {
		final var eGen = new ECKeyPairGenerator();
		final var random = new SecureRandom();
		final var gParam = new ECKeyGenerationParameters(ECKeyUtils.domain(), random);

		eGen.init(gParam);

		final var iv = new byte[KEY_SIZE / 8];
		new SecureRandom().nextBytes(iv);

		final var ephemPair = eGen.generateKeyPair();
		final var prv = ((ECPrivateKeyParameters) ephemPair.getPrivate()).getD();
		final var pub = ((ECPublicKeyParameters) ephemPair.getPublic()).getQ();
		final var iesEngine = makeIESEngine(true, toPub, prv, iv);

		final var keygenParams = new ECKeyGenerationParameters(ECKeyUtils.domain(), random);
		final var generator = new ECKeyPairGenerator();
		generator.init(keygenParams);

		final var gen = new ECKeyPairGenerator();
		gen.init(new ECKeyGenerationParameters(ECKeyUtils.domain(), random));

		byte[] cipher;
		try {
			cipher = iesEngine.processBlock(plaintext, 0, plaintext.length, macData);
			final var bos = new ByteArrayOutputStream();
			bos.write(pub.getEncoded(false));
			bos.write(iv);
			bos.write(cipher);
			return bos.toByteArray();
		} catch (InvalidCipherTextException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static IESEngine makeIESEngine(boolean isEncrypt, ECPoint pub, BigInteger prv, byte[] iv) {
        final var iesEngine = new IESEngine(
            new ECDHBasicAgreement(),
            new ConcatKDFBytesGenerator(new SHA256Digest()),
            new HMac(new SHA256Digest()),
            new SHA256Digest(),
            new BufferedBlockCipher(new SICBlockCipher(new AESEngine()))
        );

		final var d = new byte[] {};
		final var e = new byte[] {};

        final var p = new IESWithCipherParameters(d, e, KEY_SIZE, KEY_SIZE);
        final var parametersWithIV = new ParametersWithIV(p, iv);
		iesEngine.init(
			isEncrypt,
			new ECPrivateKeyParameters(prv, ECKeyUtils.domain()),
			new ECPublicKeyParameters(pub, ECKeyUtils.domain()),
			parametersWithIV
		);
		return iesEngine;
	}
}
