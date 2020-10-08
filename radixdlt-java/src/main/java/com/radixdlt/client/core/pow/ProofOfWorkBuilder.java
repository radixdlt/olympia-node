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
import com.radixdlt.utils.Bytes;

import java.nio.ByteBuffer;
import java.util.BitSet;

public class ProofOfWorkBuilder {
	public ProofOfWork build(int magic, byte[] seed, int leading) {
		if (seed.length != 32 || leading < 1 || leading > 256) {
			throw new IllegalArgumentException();
		}

		BitSet targetBitSet = new BitSet(256);
		targetBitSet.set(0, 256);
		targetBitSet.clear(0, (leading / 8) * 8);
		targetBitSet.clear((leading / 8) * 8 + (8 - leading % 8), (leading / 8) * 8 + 8);
		byte[] target = targetBitSet.toByteArray();

		ByteBuffer buffer = ByteBuffer.allocate(32 + 4 + Long.BYTES);

		// Consumable getAmount cannot be 0 so start at 1
		long nonce = 1;
		buffer.putInt(magic);
		buffer.put(seed);

		String targetHex = Bytes.toHexString(target);

		while (true) {
			buffer.position(32 + 4);
			buffer.putLong(nonce);
			String hashHex = Bytes.toHexString(HashUtils.sha256(buffer.array()).asBytes());
			if (hashHex.compareTo(targetHex) < 0) {
				return new ProofOfWork(nonce, magic, seed, target);
			}
			nonce++;
		}
	}
}
