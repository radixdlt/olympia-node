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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.modes.SICBlockCipher;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.IESWithCipherParameters;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.math.ec.ECPoint;

public final class ECIESCoder {
  public static final int KEY_SIZE = 128;
  public static final int OVERHEAD_SIZE = 65 + KEY_SIZE / 8 + 32;

  private ECIESCoder() {}

  public static byte[] decrypt(BigInteger privKey, byte[] cipher)
      throws IOException, InvalidCipherTextException {
    return decrypt(privKey, cipher, null);
  }

  public static byte[] decrypt(BigInteger privKey, byte[] cipher, byte[] macData)
      throws InvalidCipherTextException {
    final var is = new ByteArrayInputStream(cipher);
    try {
      final var ephemBytes =
          new byte[2 * ((ECKeyUtils.domain().getCurve().getFieldSize() + 7) / 8) + 1];
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

  public static byte[] decrypt(
      ECPoint ephem, BigInteger prv, byte[] iv, byte[] cipher, byte[] macData)
      throws InvalidCipherTextException {
    final var iesEngine =
        new IESEngine(
            new ECDHBasicAgreement(),
            new ConcatKDFBytesGenerator(new SHA256Digest()),
            new HMac(new SHA256Digest()),
            new SHA256Digest(),
            new BufferedBlockCipher(new SICBlockCipher(new AESEngine())));

    final var d = new byte[] {};
    final var e = new byte[] {};

    final var p = new IESWithCipherParameters(d, e, KEY_SIZE, KEY_SIZE);
    final var parametersWithIV = new ParametersWithIV(p, iv);
    iesEngine.init(
        false,
        new ECPrivateKeyParameters(prv, ECKeyUtils.domain()),
        new ECPublicKeyParameters(ephem, ECKeyUtils.domain()),
        parametersWithIV);

    return iesEngine.processBlock(cipher, 0, cipher.length, macData);
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

  private static IESEngine makeIESEngine(
      boolean isEncrypt, ECPoint pub, BigInteger prv, byte[] iv) {
    final var iesEngine =
        new IESEngine(
            new ECDHBasicAgreement(),
            new ConcatKDFBytesGenerator(new SHA256Digest()),
            new HMac(new SHA256Digest()),
            new SHA256Digest(),
            new BufferedBlockCipher(new SICBlockCipher(new AESEngine())));

    final var d = new byte[] {};
    final var e = new byte[] {};

    final var p = new IESWithCipherParameters(d, e, KEY_SIZE, KEY_SIZE);
    final var parametersWithIV = new ParametersWithIV(p, iv);
    iesEngine.init(
        isEncrypt,
        new ECPrivateKeyParameters(prv, ECKeyUtils.domain()),
        new ECPublicKeyParameters(pub, ECKeyUtils.domain()),
        parametersWithIV);
    return iesEngine;
  }
}
