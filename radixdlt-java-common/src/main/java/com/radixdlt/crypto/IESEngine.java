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
import java.io.IOException;
import org.bouncycastle.crypto.BasicAgreement;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.DerivationFunction;
import org.bouncycastle.crypto.DerivationParameters;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.KeyParser;
import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.generators.EphemeralKeyPairGenerator;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.IESParameters;
import org.bouncycastle.crypto.params.IESWithCipherParameters;
import org.bouncycastle.crypto.params.KDFParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.MGFParameters;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.Pack;

/**
 * Support class for constructing integrated encryption cipher for doing basic message exchanges on
 * top of key agreement ciphers. Follows the description given in IEEE Std 1363a with a couple of
 * changes: - Hash the MAC key before use - Include the encryption IV in the MAC computation
 */
public class IESEngine {
  private final Digest hash;
  private BasicAgreement agree;
  private DerivationFunction kdf;
  private Mac mac;
  private BufferedBlockCipher cipher;

  private boolean forEncryption;
  private CipherParameters privParam, pubParam;
  private IESParameters param;

  private byte[] v;
  private EphemeralKeyPairGenerator keyPairGenerator;
  private KeyParser keyParser;
  private byte[] iv;
  boolean hashK2 = true;

  /**
   * set up for use with stream mode, where the key derivation function is used to provide a stream
   * of bytes to xor with the message.
   *
   * @param agree the key agreement used as the basis for the encryption
   * @param kdf the key derivation function used for byte generation
   * @param mac the message authentication code generator for the message
   * @param hash hash ing function
   * @param cipher the actual cipher
   */
  public IESEngine(
      BasicAgreement agree,
      DerivationFunction kdf,
      Mac mac,
      Digest hash,
      BufferedBlockCipher cipher) {
    this.agree = agree;
    this.kdf = kdf;
    this.mac = mac;
    this.hash = hash;
    this.cipher = cipher;
  }

  /**
   * Initialise the encryptor.
   *
   * @param forEncryption whether or not this is encryption/decryption.
   * @param privParam our private key parameters
   * @param pubParam the recipient's/sender's public key parameters
   * @param params encoding and derivation parameters, may be wrapped to include an IV for an
   *     underlying block cipher.
   */
  public void init(
      boolean forEncryption,
      CipherParameters privParam,
      CipherParameters pubParam,
      CipherParameters params) {
    this.forEncryption = forEncryption;
    this.privParam = privParam;
    this.pubParam = pubParam;
    this.v = new byte[0];

    extractParams(params);
  }

  /**
   * Initialise the encryptor.
   *
   * @param publicKey the recipient's/sender's public key parameters
   * @param params encoding and derivation parameters, may be wrapped to include an IV for an
   *     underlying block cipher.
   * @param ephemeralKeyPairGenerator the ephemeral key pair generator to use.
   */
  public void init(
      AsymmetricKeyParameter publicKey,
      CipherParameters params,
      EphemeralKeyPairGenerator ephemeralKeyPairGenerator) {
    this.forEncryption = true;
    this.pubParam = publicKey;
    this.keyPairGenerator = ephemeralKeyPairGenerator;

    extractParams(params);
  }

  /**
   * Initialise the encryptor.
   *
   * @param privateKey the recipient's private key.
   * @param params encoding and derivation parameters, may be wrapped to include an IV for an
   *     underlying block cipher.
   * @param publicKeyParser the parser for reading the ephemeral public key.
   */
  public void init(
      AsymmetricKeyParameter privateKey, CipherParameters params, KeyParser publicKeyParser) {
    this.forEncryption = false;
    this.privParam = privateKey;
    this.keyParser = publicKeyParser;

    extractParams(params);
  }

  private void extractParams(CipherParameters params) {
    if (params instanceof ParametersWithIV) {
      this.iv = ((ParametersWithIV) params).getIV();
      this.param = (IESParameters) ((ParametersWithIV) params).getParameters();
    } else {
      this.iv = null;
      this.param = (IESParameters) params;
    }
  }

  private byte[] encryptBlock(byte[] in, int inOff, int inLen, byte[] macData)
      throws InvalidCipherTextException {
    byte[] c, k, k1, k2;
    int len;

    if (cipher == null) {
      // Streaming mode.
      k1 = new byte[inLen];
      k2 = new byte[param.getMacKeySize() / 8];
      k = new byte[k1.length + k2.length];

      kdf.generateBytes(k, 0, k.length);
      System.arraycopy(k, 0, k1, 0, k1.length);
      System.arraycopy(k, inLen, k2, 0, k2.length);

      c = new byte[inLen];

      for (int i = 0; i != inLen; i++) {
        c[i] = (byte) (in[inOff + i] ^ k1[i]);
      }
      len = inLen;
    } else {
      // Block cipher mode.
      k1 = new byte[((IESWithCipherParameters) param).getCipherKeySize() / 8];
      k2 = new byte[param.getMacKeySize() / 8];
      k = new byte[k1.length + k2.length];

      kdf.generateBytes(k, 0, k.length);
      System.arraycopy(k, 0, k1, 0, k1.length);
      System.arraycopy(k, k1.length, k2, 0, k2.length);

      // If iv provided use it to initialise the cipher
      if (iv != null) {
        cipher.init(true, new ParametersWithIV(new KeyParameter(k1), iv));
      } else {
        cipher.init(true, new KeyParameter(k1));
      }

      c = new byte[cipher.getOutputSize(inLen)];
      len = cipher.processBytes(in, inOff, inLen, c, 0);
      len += cipher.doFinal(c, len);
    }

    // Convert the length of the encoding vector into a byte array.
    byte[] p2 = param.getEncodingV();

    // Apply the MAC.
    byte[] t = new byte[mac.getMacSize()];

    byte[] k2a;
    if (hashK2) {
      k2a = new byte[hash.getDigestSize()];
      hash.reset();
      hash.update(k2, 0, k2.length);
      hash.doFinal(k2a, 0);
    } else {
      k2a = k2;
    }
    mac.init(new KeyParameter(k2a));
    mac.update(iv, 0, iv.length);
    mac.update(c, 0, c.length);
    if (p2 != null) {
      mac.update(p2, 0, p2.length);
    }
    if (v.length != 0 && p2 != null) {
      byte[] l2 = new byte[4];
      Pack.intToBigEndian(p2.length * 8, l2, 0);
      mac.update(l2, 0, l2.length);
    }

    if (macData != null) {
      mac.update(macData, 0, macData.length);
    }

    mac.doFinal(t, 0);

    // Output the triple (V,C,T).
    final var output = new byte[v.length + len + t.length];
    System.arraycopy(v, 0, output, 0, v.length);
    System.arraycopy(c, 0, output, v.length, len);
    System.arraycopy(t, 0, output, v.length + len, t.length);
    return output;
  }

  private byte[] decryptBlock(byte[] inEnc, int inOff, int inLen, byte[] macData)
      throws InvalidCipherTextException {
    byte[] m, k, k1, k2;
    int len;

    // Ensure that the length of the input is greater than the MAC in bytes
    if (inLen <= (param.getMacKeySize() / 8)) {
      throw new InvalidCipherTextException("Length of input must be greater than the MAC");
    }

    if (cipher == null) {
      // Streaming mode.
      k1 = new byte[inLen - v.length - mac.getMacSize()];
      k2 = new byte[param.getMacKeySize() / 8];
      k = new byte[k1.length + k2.length];

      kdf.generateBytes(k, 0, k.length);
      System.arraycopy(k, 0, k1, 0, k1.length);
      System.arraycopy(k, k1.length, k2, 0, k2.length);

      m = new byte[k1.length];

      for (int i = 0; i != k1.length; i++) {
        m[i] = (byte) (inEnc[inOff + v.length + i] ^ k1[i]);
      }

      len = k1.length;
    } else {
      // Block cipher mode.
      k1 = new byte[((IESWithCipherParameters) param).getCipherKeySize() / 8];
      k2 = new byte[param.getMacKeySize() / 8];
      k = new byte[k1.length + k2.length];

      kdf.generateBytes(k, 0, k.length);
      System.arraycopy(k, 0, k1, 0, k1.length);
      System.arraycopy(k, k1.length, k2, 0, k2.length);

      // If IV provide use it to initialize the cipher
      if (iv != null) {
        cipher.init(false, new ParametersWithIV(new KeyParameter(k1), iv));
      } else {
        cipher.init(false, new KeyParameter(k1));
      }

      m = new byte[cipher.getOutputSize(inLen - v.length - mac.getMacSize())];
      len = cipher.processBytes(inEnc, inOff + v.length, inLen - v.length - mac.getMacSize(), m, 0);
      len += cipher.doFinal(m, len);
    }

    // Convert the length of the encoding vector into a byte array.
    byte[] p2 = param.getEncodingV();

    // Verify the MAC.
    int end = inOff + inLen;
    byte[] t1 = Arrays.copyOfRange(inEnc, end - mac.getMacSize(), end);

    byte[] t2 = new byte[t1.length];
    byte[] k2a;
    if (hashK2) {
      k2a = new byte[hash.getDigestSize()];
      hash.reset();
      hash.update(k2, 0, k2.length);
      hash.doFinal(k2a, 0);
    } else {
      k2a = k2;
    }
    mac.init(new KeyParameter(k2a));
    mac.update(iv, 0, iv.length);
    mac.update(inEnc, inOff + v.length, inLen - v.length - t2.length);

    if (p2 != null) {
      mac.update(p2, 0, p2.length);
    }

    if (v.length != 0 && p2 != null) {
      byte[] l2 = new byte[4];
      Pack.intToBigEndian(p2.length * 8, l2, 0);
      mac.update(l2, 0, l2.length);
    }

    if (macData != null) {
      mac.update(macData, 0, macData.length);
    }

    mac.doFinal(t2, 0);

    if (!Arrays.constantTimeAreEqual(t1, t2)) {
      throw new InvalidCipherTextException("Invalid MAC");
    }

    // Output the message.
    return Arrays.copyOfRange(m, 0, len);
  }

  public byte[] processBlock(byte[] in, int inOff, int inLen, byte[] macData)
      throws InvalidCipherTextException {
    if (forEncryption) {
      if (keyPairGenerator != null) {
        final var ephKeyPair = keyPairGenerator.generate();
        this.privParam = ephKeyPair.getKeyPair().getPrivate();
        this.v = ephKeyPair.getEncodedPublicKey();
      }
    } else {
      if (keyParser != null) {
        final var bIn = new ByteArrayInputStream(in, inOff, inLen);
        try {
          this.pubParam = keyParser.readKey(bIn);
        } catch (IOException e) {
          throw new InvalidCipherTextException(
              "unable to recover ephemeral public key: " + e.getMessage(), e);
        }

        final var encLength = (inLen - bIn.available());
        this.v = Arrays.copyOfRange(in, inOff, inOff + encLength);
      }
    }

    // Compute the common value and convert to byte array.
    agree.init(privParam);
    byte[] z =
        BigIntegers.asUnsignedByteArray(agree.getFieldSize(), agree.calculateAgreement(pubParam));

    // Create input to KDF.
    byte[] vz;
    vz = z;

    // Initialise the KDF.
    DerivationParameters kdfParam;
    if (kdf instanceof MGF1BytesGeneratorExt) {
      kdfParam = new MGFParameters(vz);
    } else {
      kdfParam = new KDFParameters(vz, param.getDerivationV());
    }
    kdf.init(kdfParam);

    return forEncryption
        ? encryptBlock(in, inOff, inLen, macData)
        : decryptBlock(in, inOff, inLen, macData);
  }
}
