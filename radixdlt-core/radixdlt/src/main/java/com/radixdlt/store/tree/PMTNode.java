package com.radixdlt.store.tree;

import com.radixdlt.crypto.HashUtils;

import java.nio.ByteBuffer;

public abstract class PMTNode {

	public enum NodeType {
		LEAF,
		EXTENSION,
		BRANCH,
		EMPTY
	}

	public final static int HASH_COND = 32;

	protected final int EVEN_SIZE = 8;
	protected final int ODD_SIZE = 4;

	protected byte[] hash;
	protected byte[] serialized;
	protected NodeType nodeType;
	protected byte[] value;

	protected PMTNode hash() {
		var ser = serialize();
		if (ser.length >= HASH_COND) {
			// TODO: what's the best hashing variant? Consider tree size
			//       Ethereum uses sha3 which is keccak-256
			this.hash = HashUtils.sha512(ser).asBytes();
		} else {
			this.hash = ser;
		}
		return this;
	}

	public byte[] getHash() {
		// TODO: optimize. Introduce isDirty/null-on-write to avoid hashing and serialization
		this.hash();
		return this.hash;
	}

	public NodeType getNodeType() {
		return nodeType;
	}

	public byte[] getValue() {
		return value;
	}

	public PMTNode setValue(byte[] value) {
		this.value = value;
		return this;
	}

	public byte[] getFirstNibble() {
		return getFirstNibble(this.hash);
	}

	public byte[] getFirstNibble(byte[] hash) {
		byte[] nibble = new byte[4];
		for(int i=0;i<=4;++i) {
			nibble[i] = hash[i];
		}
		// should we set it in a cache field for re-use?
		return nibble;
	}

	public abstract byte[] serialize();

	public static PMTNode deserialize(byte[] node){
		// 1. use deserialization protocol e.g. RLP
		// 2. classify a node (leaf, branch, ext)
		//    ...Branch vs Leaf/Exp
		//    ...use Prefix for Leaf vs Exp
		// 3. instantiate with arguments
		return null;
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

	public Integer nibbleToInteger(byte[] nibble) {
		return ByteBuffer.wrap(nibble).getInt();
	}

	public String toByteString(Integer keyNibble) {
		return Integer.toBinaryString(keyNibble);
	}
}
