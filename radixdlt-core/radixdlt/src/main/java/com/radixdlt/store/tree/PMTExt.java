package com.radixdlt.store.tree;

import com.radixdlt.store.tree.serialization.rlp.RLP;

import static com.radixdlt.store.tree.TreeUtils.applyPrefix;

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
		if (keyNibbles.isEmpty()) {
			return this.getValue();
		} else {
			var prefixedKey = applyPrefix(this.getKey().getKey(), ODD_PREFIX, EVEN_PREFIX);

			// TODO: serialize, RLP?
			this.serialized = RLP.encodeList(
					RLP.encodeElement(prefixedKey),
					RLP.encodeElement(value)
			);
			return this.serialized;
		}
	}
}
