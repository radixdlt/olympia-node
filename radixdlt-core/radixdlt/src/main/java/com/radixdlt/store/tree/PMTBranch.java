package com.radixdlt.store.tree;

public class PMTBranch extends PMTNode {

	int NUMBER_OF_NIBBLES = 16;

	private byte[][] slices;
	private int slicesCounter = 0;

	PMTBranch(PMTLeaf leaf, byte[] value) {
		this.slices = new byte[NUMBER_OF_NIBBLES][];
		setNibble(leaf);
	}

	public PMTBranch setNibble(PMTLeaf leaf) {
		var nibble = leaf.getFirstNibble();
		var sliceKey = this.nibbleToInteger(nibble);
		this.slices[sliceKey] = leaf.getHash();
		slicesCounter++;
		return this;
	}


	private PMTBranch serialize() {

		// array RLP serialization. How to serialize nulls?

		applyPrefix();
		this.serialized = new byte[0];
		return this;
	}

	private PMTBranch applyPrefix() {
		// TODO the 1,2,3,4 nimbles
		return this;
	}
}
