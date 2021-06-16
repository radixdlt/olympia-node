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
import org.bouncycastle.crypto.DerivationFunction;
import org.bouncycastle.crypto.DerivationParameters;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.params.MGFParameters;

/**
 * This class is borrowed from spongycastle project
 * The only change made is addition of 'counterStart' parameter to
 * conform to Crypto++ capabilities
 */
public final class MGF1BytesGeneratorExt implements DerivationFunction {
    private Digest digest;
    private byte[] seed;
    private int hLen;
    private int counterStart;

    public MGF1BytesGeneratorExt(Digest digest, int counterStart) {
        this.digest = digest;
        this.hLen = digest.getDigestSize();
        this.counterStart = counterStart;
    }

    public void init(DerivationParameters param) {
        if (!(param instanceof MGFParameters)) {
            throw new IllegalArgumentException("MGF parameters required for MGF1Generator");
        } else {
            this.seed = ((MGFParameters) param).getSeed();
        }
    }

    public Digest getDigest() {
        return this.digest;
    }

    private void iToOsp(int i, byte[] sp) {
        sp[0] = (byte) (i >>> 24);
        sp[1] = (byte) (i >>> 16);
        sp[2] = (byte) (i >>> 8);
        sp[3] = (byte) (i >>> 0);
    }

    public int generateBytes(byte[] out, int outOff, int len) throws DataLengthException, IllegalArgumentException {
        if (out.length - len < outOff) {
            throw new DataLengthException("output buffer too small");
        } else {
            final var hashBuf = new byte[this.hLen];
            final var c = new byte[4];
            int counter = 0;
            int hashCounter = counterStart;
            this.digest.reset();
            if (len > this.hLen) {
                do {
                    this.iToOsp(hashCounter++, c);
                    this.digest.update(this.seed, 0, this.seed.length);
                    this.digest.update(c, 0, c.length);
                    this.digest.doFinal(hashBuf, 0);
                    System.arraycopy(hashBuf, 0, out, outOff + counter * this.hLen, this.hLen);
                    ++counter;
                } while (counter < len / this.hLen);
            }

            if (counter * this.hLen < len) {
                this.iToOsp(hashCounter, c);
                this.digest.update(this.seed, 0, this.seed.length);
                this.digest.update(c, 0, c.length);
                this.digest.doFinal(hashBuf, 0);
                System.arraycopy(hashBuf, 0, out, outOff + counter * this.hLen, len - counter * this.hLen);
            }

            return len;
        }
    }
}
