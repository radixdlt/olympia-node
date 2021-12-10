package com.radixdlt.store.tree;

import com.radixdlt.store.tree.serialization.rlp.RLP;

public final class PMTLeaf extends PMTNode {

	public static final int EVEN_PREFIX = 2;
	public static final int ODD_PREFIX = 3;

	public PMTLeaf(PMTKey keyNibbles, byte[] newValue) {
		this.nodeType = NodeType.LEAF;
		this.keyNibbles = keyNibbles;
		this.value = newValue;
	}

	public byte[] serialize() {
		// INFO: leaf can have empty key. It's because value may not fit branches' hash pointer field

		var nibblesWithPrefix = TreeUtils.applyPrefix(this.getKey().getRaw(), ODD_PREFIX, EVEN_PREFIX);
		byte[] bytesWithPrefix = TreeUtils.fromNibblesToBytes(nibblesWithPrefix);
		return RLP.encodeList(
				RLP.encodeElement(bytesWithPrefix),
				RLP.encodeElement(value)
		);
	}
}
