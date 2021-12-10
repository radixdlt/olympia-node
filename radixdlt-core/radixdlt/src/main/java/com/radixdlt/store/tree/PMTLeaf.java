package com.radixdlt.store.tree;

public final class PMTLeaf extends PMTNode {

	public static final int EVEN_PREFIX = 2;
	public static final int ODD_PREFIX = 3;

	public PMTLeaf(PMTKey keyNibbles, byte[] newValue) {
		this.nodeType = NodeType.LEAF;
		this.keyNibbles = keyNibbles;
		this.value = newValue;
	}
}
