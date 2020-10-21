/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.pow;

import com.radixdlt.crypto.HashUtils;

import java.nio.ByteBuffer;

import com.radixdlt.utils.Bytes;
import org.bouncycastle.util.encoders.Base64;

public class ProofOfWork {
	private final long nonce;
	private final int magic;
	private final byte[] seed;
	private final byte[] target;

	public ProofOfWork(long nonce, int magic, byte[] seed, byte[] target) {
		this.nonce = nonce;
		this.magic = magic;
		this.seed = seed;
		this.target = target;
	}

	public String getTargetHex() {
		return Bytes.toHexString(target);
	}

	public long getNonce() {
		return nonce;
	}

	public void validate() throws ProofOfWorkException {
		String targetHex = getTargetHex();
		ByteBuffer byteBuffer = ByteBuffer.allocate(4 + 32 + Long.BYTES);
		byteBuffer.putInt(magic);
		byteBuffer.put(seed);
		byteBuffer.putLong(nonce);
		String hashHex = Bytes.toHexString(HashUtils.sha256(byteBuffer.array()).asBytes());
		if (hashHex.compareTo(targetHex) > 0) {
			throw new ProofOfWorkException(hashHex, targetHex);
		}
	}

	@Override
	public String toString() {
		return "POW: nonce(" + nonce + ") magic(" + magic + ") seed(" + Base64.toBase64String(seed) + ") target(" + getTargetHex() + ")";
	}
}
