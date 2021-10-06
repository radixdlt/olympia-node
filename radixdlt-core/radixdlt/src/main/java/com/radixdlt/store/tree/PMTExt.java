package com.radixdlt.store.tree;

import java.nio.ByteBuffer;

public class PMTExt extends PMTNode {
	public PMTKey key;        // key-part
	public byte[] value;      // universal byte array for hash pointer to branch

	// Flag to handle:
	// * leaf vs ext
	// * partial byte paths
	// TODO: Java Endianess???


	private int EVEN_PREFIX = 0;
	private int ODD_PREFIX = 1;
	private byte[] prefixedKey;

	// TODO: explicit test for Nibble prefix!!!

	byte[] getEvenPrefix() {
		return ByteBuffer.allocate(EVEN_SIZE).putInt(EVEN_PREFIX).array();
	}

	byte[] getOddPrefix() {
		return ByteBuffer.allocate(ODD_SIZE).putInt(ODD_PREFIX).array();
	}

	PMTExt(PMTKey newKey, byte[] newHashPointer) {
		nodeType = NodeType.EXTENSION;
		serialize();
		hash();
	}

	public byte[] serialize() {
		this.prefixedKey = applyPrefix(key, getOddPrefix(), getEvenPrefix());
		// TODO: serialize, RLP?
		return this.serialized = new byte[0];
	}
}
