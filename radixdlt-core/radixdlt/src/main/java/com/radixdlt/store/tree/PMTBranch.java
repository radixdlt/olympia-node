package com.radixdlt.store.tree;

import java.util.Arrays;

public class PMTBranch extends PMTNode {

	int NUMBER_OF_NIBBLES = 16;

	private byte[][] slices;
	private int slicesCounter = 0; // INFO: for removal

	PMTBranch(byte[] value, PMTNode... nextNode) {
		this.slices = new byte[NUMBER_OF_NIBBLES][];
		Arrays.stream(nextNode).forEach(l -> setNibble(l));
		if (value != null) {
			this.value = value;
		}
	}

	public byte[] getNextHash(PMTKey key) {
		var nib = key.getFirstNibble().toByte();
		var nibInt = TreeUtils.nibbleToInteger(nib);
		return slices[nibInt];
	}

	public PMTBranch setNibble(PMTKey nibble, PMTNode nextNode) {
		var sliceKey = TreeUtils.nibbleToInteger(nibble.toByte());
		if (this.slices[sliceKey] == null) {
			slicesCounter++;
		}
		this.slices[sliceKey] = nextNode.getHash();
		return this;
	}

	public PMTBranch setNibble(PMTNode nextNode) {
		return setNibble(nextNode.getBranchNibble(), nextNode);
	}

	public byte[] serialize() {
		// TODO: serilize, RLP? Array RLP serialization. How to serialize nulls?
		return this.serialized = "Branch serialized".getBytes();
	}

}
