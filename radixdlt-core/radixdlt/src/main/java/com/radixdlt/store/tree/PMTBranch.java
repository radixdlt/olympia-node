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
		var nib = key.getKey()[0];
		return slices[nib];
	}

	public PMTBranch setNibble(PMTKey nibble, PMTNode nextNode) {
		var sliceKey = nibble.getFirstNibbleValue();
		if (this.slices[sliceKey] == null) {
			slicesCounter++;
		}
		this.slices[sliceKey] = nextNode.getHash();
		return this;
	}

	public PMTBranch setNibble(PMTNode nextNode) {
		return setNibble(nextNode.getBranchNibble(), nextNode);
	}

	public PMTBranch copyForEdit() {
		try {
			return (PMTBranch) this.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
			throw new IllegalStateException("Can't clone branch for edits");
		}
	}

	public byte[] serialize() {
		// TODO: serilize, RLP? Array RLP serialization. How to serialize nulls?
		return this.serialized = "bran".getBytes();
	}

}
