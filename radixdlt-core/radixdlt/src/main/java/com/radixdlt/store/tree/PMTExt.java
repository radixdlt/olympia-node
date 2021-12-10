package com.radixdlt.store.tree;

import com.radixdlt.store.tree.serialization.rlp.RLP;

public final class PMTExt extends PMTNode {

	public static final int EVEN_PREFIX = 0;
	public static final int ODD_PREFIX = 1;

	public PMTExt(PMTKey keyNibbles, byte[] newHashPointer) {
		if(keyNibbles.isEmpty()){
			throw new IllegalArgumentException("Extensions must have non empty key-part");
		} else {
			nodeType = NodeType.EXTENSION;
			this.keyNibbles = keyNibbles;
			this.value = newHashPointer;
		}
	}

	public byte[] serialize() {
		var nibblesWithPrefix = TreeUtils.applyPrefix(this.getKey().getRaw(), ODD_PREFIX, EVEN_PREFIX);
		byte[] bytesWithPrefix = TreeUtils.fromNibblesToBytes(nibblesWithPrefix);
		return RLP.encodeList(
				RLP.encodeElement(bytesWithPrefix),
				RLP.encodeElement(value)
		);
	}
}
