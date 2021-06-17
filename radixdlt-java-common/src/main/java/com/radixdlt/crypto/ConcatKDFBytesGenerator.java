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

import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.DerivationParameters;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.DigestDerivationFunction;
import org.bouncycastle.crypto.params.ISO18033KDFParameters;
import org.bouncycastle.crypto.params.KDFParameters;
import org.bouncycastle.util.Pack;

/**
 * Basic KDF generator for derived keys and ivs as defined by NIST SP 800-56A.
 */
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

    public int generateBytes(byte[] out, int outOff, int len) throws DataLengthException, IllegalArgumentException {
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
