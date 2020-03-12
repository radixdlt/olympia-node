package com.radixdlt.client.core.pow;

import com.radixdlt.crypto.Hash;
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
			String hashHex = Bytes.toHexString(Hash.hash256(buffer.array()));
			if (hashHex.compareTo(targetHex) < 0) {
				return new ProofOfWork(nonce, magic, seed, target);
			}
			nonce++;
		}
	}
}
