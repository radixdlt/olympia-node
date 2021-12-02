package com.radixdlt.store.tree;

import com.radixdlt.store.tree.serialization.rlp.RLP;

public class PMTExt extends PMTNode {

	private static final int EVEN_PREFIX = 0;
	private static final int ODD_PREFIX = 1;

	PMTExt(PMTKey allNibbles, byte[] newHashPointer) {
		this(null, allNibbles, newHashPointer);
	}

	PMTExt(PMTKey branchNibble, PMTKey keyNibbles, byte[] newHashPointer) {
		nodeType = NodeType.EXTENSION; // refactor to casting or pattern
		this.branchNibble = branchNibble;
		this.keyNibbles = keyNibbles;
		this.value = newHashPointer;
	}

	public byte[] serialize() {
		// INFO: It doesn't make sense for Extension to have empty key-part.
		//       We rewrite hash pointer to Branches' nibble position
		// TODO check if this is correct, we should probably forbid this state in the constructor
		if (keyNibbles.isEmpty()) {
			return this.getValue();
		} else {
			var nibblesWithPrefix = TreeUtils.applyPrefix(this.getKey().getKey(), ODD_PREFIX, EVEN_PREFIX);
			byte[] bytesWithPrefix = TreeUtils.fromNibblesToBytes(nibblesWithPrefix);
			return RLP.encodeList(
					RLP.encodeElement(bytesWithPrefix),
					RLP.encodeElement(value)
			);
		}
	}
}
