package com.radixdlt.store.tree;

import java.util.Arrays;
import java.util.function.Function;

public final class PMTLeaf extends PMTNode {

	public static final int EVEN_PREFIX = 2;
	public static final int ODD_PREFIX = 3;

	public PMTLeaf(PMTKey keyNibbles, byte[] newValue) {
		this.keyNibbles = keyNibbles;
		this.value = newValue;
	}

	public PMTAcc insertNode(PMTKey key, byte[] val, PMTAcc acc, Function<PMTNode, byte[]> represent, Function<byte[], PMTNode> read) {
		final PMTPath commonPath = PMTPath.findCommonPath(this.getKey(), key);
		switch (commonPath.whichRemainderIsLeft()) {
			case EXISTING:
				acc = handleExistingRemainder(val, acc, represent, commonPath);
				break;
			case NEW:
				acc = handleNewRemainder(val, acc, represent, commonPath);
				break;
			case EXISTING_AND_NEW:
				acc = handleBothExistingAndNewRemainder(val, acc, represent, commonPath);
				break;
			case NONE:
				acc = handleNoRemainder(key, val, acc);
				break;
			default:
				throw new IllegalStateException(String.format("Unexpected subtree: %s", commonPath.whichRemainderIsLeft()));
		}
		return acc;
	}

	private PMTAcc handleNoRemainder(PMTKey key, byte[] val, PMTAcc acc) {
		if (Arrays.equals(val, this.getValue())) {
			acc.setTip(this);
		} else {
			// INFO: we preserve whole key-end as there are no branches
			var newLeaf = new PMTLeaf(key, val);
			acc.setTip(newLeaf);
			acc.add(newLeaf);
			acc.mark(this);
		}
		return acc;
	}

	private PMTAcc handleBothExistingAndNewRemainder(byte[] val, PMTAcc acc, Function<PMTNode, byte[]> represent, PMTPath commonPath) {
		PMTBranch newBranch;
		var remainderNew = commonPath.getRemainder(PMTPath.RemainingSubtree.NEW);
		var remainderOld = commonPath.getRemainder(PMTPath.RemainingSubtree.EXISTING);
		var newLeafNew = new PMTLeaf(remainderNew.getTailNibbles(), val);
		var newLeafOld = new PMTLeaf(remainderOld.getTailNibbles(), this.value);
		newBranch = new PMTBranch(
				null,
				new PMTBranch.PMTBranchChild(remainderNew.getFirstNibble(), represent.apply(newLeafNew)),
				new PMTBranch.PMTBranchChild(remainderOld.getFirstNibble(), represent.apply(newLeafOld))
		);
		acc = computeAndSetTip(commonPath, newBranch, acc, represent);
		acc.add(newLeafNew, newLeafOld, newBranch);
		acc.mark(this);
		return acc;
	}

	private PMTAcc handleNewRemainder(byte[] val, PMTAcc acc, Function<PMTNode, byte[]> represent, PMTPath commonPath) {
		PMTKey remainder;
		PMTBranch newBranch;
		PMTLeaf newLeaf;
		remainder = commonPath.getRemainder(PMTPath.RemainingSubtree.NEW);
		newLeaf = new PMTLeaf(remainder.getTailNibbles(), val);
		newBranch = new PMTBranch(
				this.getValue(),
				new PMTBranch.PMTBranchChild(remainder.getFirstNibble(), represent.apply(newLeaf))
		);
		acc = computeAndSetTip(commonPath, newBranch, acc, represent);
		acc.add(newLeaf, newBranch);
		acc.mark(this);
		return acc;
	}

	private PMTAcc handleExistingRemainder(byte[] val, PMTAcc acc, Function<PMTNode, byte[]> represent, PMTPath commonPath) {
		var remainder = commonPath.getRemainder(PMTPath.RemainingSubtree.EXISTING);
		var newLeaf = new PMTLeaf(remainder.getTailNibbles(), this.getValue());
		var newBranch = new PMTBranch(
				val,
				new PMTBranch.PMTBranchChild(remainder.getFirstNibble(), represent.apply(newLeaf))
		);
		acc = computeAndSetTip(commonPath, newBranch, acc, represent);
		acc.add(newLeaf, newBranch);
		acc.mark(this);
		return acc;
	}

	public PMTAcc getValue(PMTKey key, PMTAcc acc, Function<byte[], PMTNode> read) {
		final PMTPath commonPath = PMTPath.findCommonPath(this.getKey(), key);
		switch (commonPath.whichRemainderIsLeft()) {
			case NONE:
				acc.setRetVal(this);
				break;
			case NEW, EXISTING, EXISTING_AND_NEW:
				acc.mark(this);
				acc.setNotFound();
				break;
			default:
				throw new IllegalStateException(String.format("Unexpected subtree: %s", commonPath.whichRemainderIsLeft()));
		}
		return acc;
	}
}
