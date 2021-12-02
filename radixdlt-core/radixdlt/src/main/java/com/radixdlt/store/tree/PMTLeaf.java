package com.radixdlt.store.tree;

import com.radixdlt.store.tree.serialization.rlp.RLP;

public class PMTLeaf extends PMTNode {

	private static final int EVEN_PREFIX = 2;
	private static final int ODD_PREFIX = 3;

	public PMTLeaf(PMTKey allNibbles, byte[] newValue) {
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

		var nibblesWithPrefix = TreeUtils.applyPrefix(this.getKey().getKey(), ODD_PREFIX, EVEN_PREFIX);
		byte[] bytesWithPrefix = TreeUtils.fromNibblesToBytes(nibblesWithPrefix);
		return RLP.encodeList(
				RLP.encodeElement(bytesWithPrefix),
				RLP.encodeElement(value)
		);
	}
}
