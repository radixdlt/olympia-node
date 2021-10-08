package com.radixdlt.store.tree;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static com.radixdlt.store.tree.TreeUtils.applyPrefix;

public class PMTExt extends PMTNode {

	final private int EVEN_PREFIX = 0;
	final private int ODD_PREFIX = 1;
	private byte[] prefixedKey;

	// TODO: explicit test for Nibble prefix! Check java Endianness

	byte[] getEvenPrefix() {
		return ByteBuffer.allocate(8).putInt(EVEN_PREFIX).array();
	}

	byte[] getOddPrefix() {
		return ByteBuffer.allocate(4).putInt(ODD_PREFIX).array();
	}

	PMTExt(PMTKey allNibbles, byte[] newHashPointer) {
		this(null, allNibbles, newHashPointer);
	}

	PMTExt(PMTKey firstNibble, PMTKey tailNibbles, byte[] newHashPointer) {
		nodeType = NodeType.EXTENSION; // refactor to casting or pattern
		this.firstNibble = firstNibble;
		this.tailNibbles = tailNibbles;
		this.value = newHashPointer;
	}

	public byte[] serialize() {
		// INFO: It doesn't make sense for Extension to have empty key-part.
		//       We rewrite hash pointer to Branches' nibble position
		if (tailNibbles.isEmpty()) {
			return this.getValue();
		} else {
			this.prefixedKey = applyPrefix(this.tailNibbles.toByte(), getOddPrefix(), getEvenPrefix());

			// TODO: serialize, RLP?
			return this.serialized = "Ext serialized".getBytes();
		}
	}
}
