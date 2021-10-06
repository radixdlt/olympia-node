package com.radixdlt.store.tree;

import com.radixdlt.crypto.HashUtils;

import java.nio.ByteBuffer;

public class PMTLeaf extends PMTNode {

	public PMTKey key;        // key-end
	public byte[] value;      // universal byte array

	private int EVEN_PREFIX = 2;
	private int ODD_PREFIX = 3;
	private byte[] prefixedKey;

	byte[] getEvenPrefix() {
		return ByteBuffer.allocate(8).putInt(EVEN_PREFIX).array();
	}

	byte[] getOddPrefix() {
		return ByteBuffer.allocate(4).putInt(ODD_PREFIX).array();
	}

	PMTLeaf(PMTKey newKey, byte[] newValue) {
		nodeType = NodeType.LEAF;
		serialize();
		hash();
	}

	public byte[] serialize() {
		this.prefixedKey = applyPrefix(key, getOddPrefix(), getEvenPrefix());
		// TODO: serialize, RLP?
		return this.serialized = new byte[0];
	}
}
