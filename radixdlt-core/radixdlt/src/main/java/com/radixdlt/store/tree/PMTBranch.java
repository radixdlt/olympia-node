package com.radixdlt.store.tree;

import com.radixdlt.store.tree.serialization.rlp.RLP;

import java.util.Arrays;

public class PMTBranch extends PMTNode {

	private static final int NUMBER_OF_NIBBLES = 16;

	private byte[][] slices;
	private int slicesCounter = 0; // INFO: for removal


	PMTBranch(byte[] value, PMTNode... nextNode) {
		this.slices = new byte[NUMBER_OF_NIBBLES][];
		for (int i = 0; i < NUMBER_OF_NIBBLES; i++) {
			slices[i] = new byte[]{};
		}
		Arrays.stream(nextNode).forEach(l -> setNibble(l));
		if (value != null) {
			this.value = value;
		}
		this.nodeType = NodeType.BRANCH;
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
		byte[][] slicesRLPEncoded = new byte[NUMBER_OF_NIBBLES][];
		for (int i = 0; i < NUMBER_OF_NIBBLES; i++) {
			slicesRLPEncoded[i] = RLP.encodeElement(slices[i]);
		}
		byte[] finalSlicesRLPEncoded = RLP.encodeList(slicesRLPEncoded);
		if (value != null) {
			this.serialized = RLP.encodeList(finalSlicesRLPEncoded, value);
		} else {
			this.serialized = finalSlicesRLPEncoded;
		}

		return this.serialized;
	}

}
