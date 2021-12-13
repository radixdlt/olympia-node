package com.radixdlt.store.tree;

import com.google.common.base.Objects;

import java.util.Arrays;
import java.util.function.Function;

public final class PMTBranch extends PMTNode {

	public static final int NUMBER_OF_NIBBLES = 16;

	private byte[][] children;

	record PMTBranchChild(PMTKey branchNibble, byte[] representation) {

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			PMTBranchChild that = (PMTBranchChild) o;
			return Objects.equal(branchNibble, that.branchNibble)
					&& Arrays.equals(representation, that.representation);
		}

		@Override
		public int hashCode() {
			int result = Objects.hashCode(branchNibble);
			result = 31 * result + Arrays.hashCode(representation);
			return result;

		}

		@Override
		public String toString() {
			return "PMTBranchChild{"
					+ "branchNibble=" + branchNibble
					+ ", representation=" + Arrays.toString(representation)
					+ '}';
		}
	}

	public PMTAcc insertNode(
			PMTKey key,
			byte[] val,
			PMTAcc acc,
			Function<PMTNode, byte[]> represent,
			Function<byte[], PMTNode> read
	) {
		// INFO: OLD branch always has empty key and remainder
		final PMTPath commonPath = PMTPath.findCommonPath(this.getKey(), key);
		if (commonPath.whichRemainderIsLeft() == PMTPath.RemainingSubtree.NONE) {
			acc = handleNoRemainder(val, acc);
		} else if (commonPath.whichRemainderIsLeft() == PMTPath.RemainingSubtree.NEW) {
			acc = handleNewRemainder(key, val, acc, represent, read);
		} else {
			throw new IllegalStateException(String.format("Unexpected subtree: %s", commonPath.whichRemainderIsLeft()));
		}
		return acc;
	}

	private PMTAcc handleNoRemainder(byte[] val, PMTAcc acc) {
		var modifiedBranch = cloneBranch(this);
		modifiedBranch.setValue(val);
		acc.setTip(modifiedBranch);
		acc.add(modifiedBranch);
		acc.mark(this);
		return acc;
	}

	private PMTAcc handleNewRemainder(
			PMTKey key,
			byte[] val,
			PMTAcc acc,
			Function<PMTNode, byte[]> represent,
			Function<byte[], PMTNode> read) {
		var nextHash = this.getNextHash(key);
		PMTNode subTip;
		if (nextHash == null || nextHash.length == 0) {
			var newLeaf = new PMTLeaf(key.getTailNibbles(), val);
			acc.add(newLeaf);
			subTip = newLeaf;
		} else {
			var nextNode = read.apply(nextHash);
			acc = nextNode.insertNode(key.getTailNibbles(), val, acc, represent, read);
			subTip = acc.getTip();
		}
		var branchWithNext = cloneBranch(this);
		branchWithNext.setNibble(new PMTBranchChild(key.getFirstNibble(), represent.apply(subTip)));
		acc.setTip(branchWithNext);
		acc.add(branchWithNext);
		acc.mark(this);
		return acc;
	}

	public PMTAcc getValue(PMTKey key, PMTAcc acc, Function<byte[], PMTNode> read) {
		final PMTPath commonPath = PMTPath.findCommonPath(this.getKey(), key);
		if (commonPath.whichRemainderIsLeft() == PMTPath.RemainingSubtree.NONE) {
			acc.setRetVal(this);
		} else if (commonPath.whichRemainderIsLeft() == PMTPath.RemainingSubtree.NEW) {
			acc.mark(this);
			var nextHash = this.getNextHash(key);
			if (nextHash == null) {
				acc.setNotFound();
			} else {
				var nextNode = read.apply(nextHash);
				acc = nextNode.getValue(key.getTailNibbles(), acc, read);
			}
		} else {
			throw new IllegalStateException(String.format("Unexpected subtree: %s", commonPath.whichRemainderIsLeft()));
		}
		return acc;
	}

	PMTBranch cloneBranch(PMTBranch currentBranch) {
		return currentBranch.copyForEdit();
	}

	 public PMTBranch(byte[][] children, byte[] value) {
		this.children = children;
		this.value = value;
	}

	public PMTBranch(byte[] value, PMTBranchChild... nextNode) {
		this.children = new byte[NUMBER_OF_NIBBLES][];
		Arrays.fill(children, new byte[0]);
		Arrays.stream(nextNode).forEach(l -> setNibble(l));
		if (value != null) {
			this.value = value;
		}
	}

	public byte[][] getChildren() {
		return children;
	}

	public byte[] getNextHash(PMTKey key) {
		var nib = key.getRaw()[0];
		return children[nib];
	}

	public PMTBranch setNibble(PMTBranchChild nextNode) {
		var childrenKey = nextNode.branchNibble.getFirstNibbleValue();
		this.children[childrenKey] = nextNode.representation;
		return this;
	}

	public PMTBranch copyForEdit() {
		try {
			return (PMTBranch) this.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
			throw new IllegalStateException("Can't clone branch for edits");
		}
	}
}
