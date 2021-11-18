package com.radixdlt.store.tree;

import java.nio.ByteBuffer;

public class PMTLeaf extends PMTNode {

	final private int EVEN_PREFIX = 2;
	final private int ODD_PREFIX = 3;
	private byte[] prefixedKey;
	byte[] getEvenPrefix() {
		return ByteBuffer.allocate(8).putInt(EVEN_PREFIX).array();
	}
	byte[] getOddPrefix() {
		return ByteBuffer.allocate(4).putInt(ODD_PREFIX).array();
	}

	PMTLeaf(PMTKey allNibbles, byte[] newValue) {
		this(null, allNibbles, newValue);
	}

	PMTLeaf(PMTKey branchNibble, PMTKey keyNibbles, byte[] newValue) {
		this.nodeType = NodeType.LEAF;
		this.branchNibble = branchNibble;
		this.keyNibbles = keyNibbles;
		this.value = newValue;
	}

	public byte[] serialize() {
		// INFO: leaf can have empty key. It's because value may not fit branches' hash pointer field

		// XXX TODO: this is probably wrong on bit level?!
		this.prefixedKey = TreeUtils.applyPrefix(this.getKey().getKey(), getOddPrefix(), getEvenPrefix());
		// TODO: serialize, RLP?
		this.serialized = "leaf".getBytes();
		return this.serialized;
	}
}
