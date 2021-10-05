package com.radixdlt.store.tree;

import com.radixdlt.crypto.HashUtils;

import java.nio.ByteBuffer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class PMTNode {

	public enum NodeType {
		LEAF,
		EXTENSION,
		BRANCH,
		EMPTY
	}

	private final int HASH_COND = 32;

	protected final int EVEN_SIZE = 8;
	protected final int ODD_SIZE = 4;

	protected byte[] hash;
	protected byte[] serialized;
	protected NodeType nodeType;
	protected byte[] value;

	protected PMTNode hash() {
		if (this.serialized.length >= HASH_COND) {
			// TODO: what's the best hashing variant? Consider tree size
			this.hash = HashUtils.sha512(this.serialized).asBytes();
		} else {
			this.hash = this.serialized;
		}
		return this;
	}

	public byte[] getHash() {
		return this.hash;
	}

	public NodeType getNodeType() {
		return nodeType;
	}

	public byte[] getValue() {
		return value;
	}

	public byte[] getFirstNibble() {
		byte[] nibble = new byte[4];
		for(int i=0;i<=4;++i) {
			nibble[i] = this.hash[i];
		}
		// should we set it in a field for re-use?
		return nibble;
	}

	protected byte[] applyPrefix(PMTKey key, byte[] oddPrefix, byte[] evenPrefix) {
		var rawKey = key.toByte();
		var keyLength = rawKey.length;
		byte[] prefixed;

		if (keyLength % 8 == 0) {
			ByteBuffer bb = ByteBuffer.allocate(EVEN_SIZE + keyLength);
			bb.put(evenPrefix);
			bb.put(rawKey);
			prefixed = bb.array();
		} else if (keyLength % 4 == 0) {
			ByteBuffer bb = ByteBuffer.allocate(ODD_SIZE + keyLength);
			bb.put(oddPrefix);
			bb.put(rawKey);
			prefixed = bb.array();
		} else {
			// TODO: throw exception here.
			prefixed = null;
		}
		return prefixed;
	}

	public String toHexString(byte[] byteBuffer) {
		return IntStream.range(0, byteBuffer.length)
			.map(i -> byteBuffer[i] & 0xff)
			.mapToObj(b -> String.format("%02x", b))
			.collect(Collectors.joining());
		}

	public Integer nibbleToInteger(byte[] nibble) {
		return ByteBuffer.wrap(nibble).getInt();
	}

	public String toByteString(Integer keyNibble) {
		return Integer.toBinaryString(keyNibble);
	}
}
