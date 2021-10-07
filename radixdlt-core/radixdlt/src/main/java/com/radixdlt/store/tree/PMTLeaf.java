package com.radixdlt.store.tree;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static com.radixdlt.store.tree.TreeUtils.applyPrefix;

public class PMTLeaf extends PMTNode {

	final private int EVEN_PREFIX = 2;
	final private int ODD_PREFIX = 3;
	private byte[] prefixedKey;
	private Boolean overlap;

	byte[] getEvenPrefix() {
		return ByteBuffer.allocate(8).putInt(EVEN_PREFIX).array();
	}

	byte[] getOddPrefix() {
		return ByteBuffer.allocate(4).putInt(ODD_PREFIX).array();
	}

	PMTLeaf(PMTKey newKey, byte[] newValue, Boolean overlap) {
		this.nodeType = NodeType.LEAF; // refactor to casting or pattern
		this.key = newKey;
		this.value = newValue;
		this.overlap = overlap;
	}

	public byte[] serialize() {
		// INFO: Skip overlap nibble encoded in branch position
		if (overlap) {
			var subkey = Arrays.copyOfRange(this.key.toByte(), 4, this.key.toByte().length);
			this.prefixedKey = applyPrefix(subkey, getOddPrefix(), getEvenPrefix());
		} else {
			this.prefixedKey = applyPrefix(this.key.toByte(), getOddPrefix(), getEvenPrefix());
		}
		// TODO: serialize, RLP?
		return this.serialized = "Leaf serialized".getBytes();
	}
}
