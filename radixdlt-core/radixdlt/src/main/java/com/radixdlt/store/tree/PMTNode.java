package com.radixdlt.store.tree;

import java.util.Arrays;
import java.util.Objects;

public abstract class PMTNode implements Cloneable {

	public enum NodeType {
		LEAF,
		EXTENSION,
		BRANCH,
		EMPTY
	}

	public static final int DB_SIZE_COND = 32;

	protected byte[] hash;
	protected NodeType nodeType;
	protected PMTKey branchNibble;
	protected PMTKey keyNibbles;
	protected byte[] value;

	protected PMTKey getBranchNibble() {
		return branchNibble;
	}

	public PMTKey getKey() {
		return this.keyNibbles;
	}

	public NodeType getNodeType() {
		return nodeType;
	}

	public byte[] getValue() {
		return value;
	}

	public PMTNode setValue(byte[] value) {
		if (this.value == value) {
			throw new IllegalArgumentException("Nothing changed");
		} else {
			this.value = value;
		}
		return this;
	}

	public abstract byte[] serialize();

	public static PMTNode deserialize(byte[] node) {
		// 1. use deserialization protocol e.g. RLP
		// 2. classify a node (leaf, branch, ext)
		//    ...Branch vs Leaf/Exp
		//    ...use Prefix for Leaf vs Exp
		// 3. instantiate with arguments
		return null;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		PMTNode pmtNode = (PMTNode) o;
		return Arrays.equals(hash, pmtNode.hash)
				&& nodeType == pmtNode.nodeType
				&& Objects.equals(branchNibble, pmtNode.branchNibble)
				&& Objects.equals(keyNibbles, pmtNode.keyNibbles)
				&& Arrays.equals(value, pmtNode.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(hash, nodeType, branchNibble, keyNibbles, value);
	}
}
