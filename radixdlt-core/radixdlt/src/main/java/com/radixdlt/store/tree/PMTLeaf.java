package com.radixdlt.store.tree;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static com.radixdlt.store.tree.TreeUtils.applyPrefix;

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
	PMTLeaf(PMTKey firstNibble, PMTKey tailNibbles, byte[] newValue) {
		this.nodeType = NodeType.LEAF; // refactor to casting or pattern
		this.firstNibble = firstNibble;
		this.tailNibbles = tailNibbles;
		this.value = newValue;
	}

	public byte[] serialize() {
		// INFO: leaf can have empty key. It's because value may not fit branches' hash pointer field
		this.prefixedKey = applyPrefix(this.tailNibbles.toByte(), getOddPrefix(), getEvenPrefix());
		// TODO: serialize, RLP?
		return this.serialized = "Leaf serialized".getBytes();
	}
}
