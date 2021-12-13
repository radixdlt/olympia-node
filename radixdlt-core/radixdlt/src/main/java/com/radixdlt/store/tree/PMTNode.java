package com.radixdlt.store.tree;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

public abstract sealed class PMTNode implements Cloneable permits PMTBranch, PMTExt, PMTLeaf {

	public static final int DB_SIZE_COND = 32;

	protected byte[] hash;
	protected PMTKey keyNibbles;
	protected byte[] value;

	public PMTKey getKey() {
		return this.keyNibbles;
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

	public abstract PMTAcc insertNode(PMTKey key, byte[] val, PMTAcc acc, Function<PMTNode, byte[]> represent, Function<byte[], PMTNode> read);

	public abstract PMTAcc getValue(PMTKey key, PMTAcc acc, Function<byte[], PMTNode> read);

	public PMTAcc computeAndSetTip(PMTPath pmtPath, PMTBranch branch, PMTAcc acc, Function<PMTNode, byte[]> represent) {
		if (pmtPath.getCommonPrefix().isEmpty()) {
			acc.setTip(branch);
			return acc;
		} else {
			var newExt = new PMTExt(pmtPath.getCommonPrefix(), represent.apply(branch));
			acc.setTip(newExt);
			acc.add(newExt);
			return acc;
		}
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
				&& Objects.equals(keyNibbles, pmtNode.keyNibbles)
				&& Arrays.equals(value, pmtNode.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(hash), keyNibbles, Arrays.hashCode(value));
	}
}
