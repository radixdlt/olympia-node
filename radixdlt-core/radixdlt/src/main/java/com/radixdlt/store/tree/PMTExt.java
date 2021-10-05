package com.radixdlt.store.tree;

import java.nio.ByteBuffer;

public class PMTExt extends PMTNode {
	public PMTKey key;        // key-part
	public byte[] value;      // universal byte array for hash pointer to branch

	private byte[] serialized;

	// flag to handle:
	// * leaf vs ext
	// * not full byte paths
	// TODO: Java Endianess???


	private int EVEN_PREFIX = 0;
	private int ODD_PREFIX = 1;

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

	private PMTExt serialize() {
		var prefixedKey = applyPrefix(key, getOddPrefix(), getEvenPrefix());
		// TODO: serialize, RLP?
		this.serialized = new byte[0];
		return this;
	}

}
