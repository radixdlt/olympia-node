package com.radixdlt.store.tree;

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

		// XXX TODO: this is probably wrong on bit level?!
		var prefixedKey = TreeUtils.applyPrefix(this.getKey().getKey(), ODD_PREFIX, EVEN_PREFIX);
		// TODO: serialize, RLP?
		var serialized = "leaf".getBytes();
		return serialized;
	}
}