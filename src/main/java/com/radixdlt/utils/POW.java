package com.radixdlt.utils;

import com.radixdlt.crypto.Hash;
import java.nio.ByteBuffer;
import java.util.Objects;

public class POW {
	private final int magic;
	private final Hash seed;
	private final long nonce;
	private final ByteBuffer buffer = ByteBuffer.allocate(32+4+Long.BYTES);

	public POW(int magic, Hash seed) {
		this(magic, seed, Long.MIN_VALUE);
	}

	public POW(int magic, Hash seed, long nonce) {
		Objects.requireNonNull(seed);

		this.magic = magic;
		this.seed = seed;
		this.nonce = nonce;
	}

	public int getMagic() {
		return magic;
	}

	public Hash getSeed() {
		return seed;
	}

	public long getNonce() {
		return nonce;
	}

	public synchronized Hash getHash() {
		buffer.clear();
		buffer.putInt(magic);
		buffer.put(seed.toByteArray());
		buffer.putLong(nonce);
		buffer.flip();

		return new Hash(Hash.hash256(buffer.array()));
	}
}
