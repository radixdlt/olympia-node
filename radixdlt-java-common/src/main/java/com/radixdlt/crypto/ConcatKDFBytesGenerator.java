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

import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.DerivationParameters;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.DigestDerivationFunction;
import org.bouncycastle.crypto.params.ISO18033KDFParameters;
import org.bouncycastle.crypto.params.KDFParameters;
import org.bouncycastle.util.Pack;

/** Basic KDF generator for derived keys and ivs as defined by NIST SP 800-56A. */
public class ConcatKDFBytesGenerator implements DigestDerivationFunction {
  private int counterStart;
  private Digest digest;
  private byte[] shared;
  private byte[] iv;

  protected ConcatKDFBytesGenerator(int counterStart, Digest digest) {
    this.counterStart = counterStart;
    this.digest = digest;
  }

  public ConcatKDFBytesGenerator(Digest digest) {
    this(1, digest);
  }

  public void init(DerivationParameters param) {
    if (param instanceof KDFParameters) {
      final var p = (KDFParameters) param;

      shared = p.getSharedSecret();
      iv = p.getIV();
    } else if (param instanceof ISO18033KDFParameters) {
      final var p = (ISO18033KDFParameters) param;

      shared = p.getSeed();
      iv = null;
    } else {
      throw new IllegalArgumentException("KDF parameters required for KDF2Generator");
    }
  }

  public Digest getDigest() {
    return digest;
  }

  public int generateBytes(byte[] out, int outOff, int len)
      throws DataLengthException, IllegalArgumentException {
    if ((out.length - len) < outOff) {
      throw new DataLengthException("output buffer too small");
    }

    long oBytes = len;
    int outLen = digest.getDigestSize();

    // this is at odds with the standard implementation, the
    // maximum value should be hBits * (2^32 - 1) where hBits
    // is the digest output size in bits. We can't have an
    // array with a long index at the moment...
    //
    if (oBytes > ((2L << 32) - 1)) {
      throw new IllegalArgumentException("Output length too large");
    }

    final var cThreshold = (int) ((oBytes + outLen - 1) / outLen);
    final var dig = new byte[digest.getDigestSize()];
    final var c = new byte[4];
    Pack.intToBigEndian(counterStart, c, 0);

    int counterBase = counterStart & ~0xFF;

    for (int i = 0; i < cThreshold; i++) {
      digest.update(c, 0, c.length);
      digest.update(shared, 0, shared.length);

      if (iv != null) {
        digest.update(iv, 0, iv.length);
      }

      digest.doFinal(dig, 0);

      if (len > outLen) {
        System.arraycopy(dig, 0, out, outOff, outLen);
        outOff += outLen;
        len -= outLen;
      } else {
        System.arraycopy(dig, 0, out, outOff, len);
      }

      if (++c[3] == 0) {
        counterBase += 0x100;
        Pack.intToBigEndian(counterBase, c, 0);
      }
    }

    digest.reset();

    return (int) oBytes;
  }
}
