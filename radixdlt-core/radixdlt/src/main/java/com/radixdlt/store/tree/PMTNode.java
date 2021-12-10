package com.radixdlt.store.tree;

import java.util.Arrays;
import java.util.Objects;

public abstract sealed class PMTNode implements Cloneable permits PMTBranch, PMTExt, PMTLeaf {

	public enum NodeType {
		LEAF,
		EXTENSION,
		BRANCH,
		EMPTY
	}

	public static final int DB_SIZE_COND = 32;

	protected byte[] hash;
	protected NodeType nodeType;
	protected PMTKey keyNibbles;
	protected byte[] value;

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
				&& Objects.equals(keyNibbles, pmtNode.keyNibbles)
				&& Arrays.equals(value, pmtNode.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(hash), nodeType, keyNibbles, Arrays.hashCode(value));
	}
}
