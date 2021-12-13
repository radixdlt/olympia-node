package com.radixdlt.store.tree;

import java.util.function.Function;

public final class PMTExt extends PMTNode {

	public static final int EVEN_PREFIX = 0;
	public static final int ODD_PREFIX = 1;

	public PMTExt(PMTKey keyNibbles, byte[] newHashPointer) {
		if (keyNibbles.isEmpty()) {
			throw new IllegalArgumentException("Extensions must have non empty key-part");
		} else {
			this.keyNibbles = keyNibbles;
			this.value = newHashPointer;
		}
	}

	public PMTAcc insertNode(
			PMTKey key,
			byte[] val,
			PMTAcc acc,
			Function<PMTNode, byte[]> represent,
			Function<byte[], PMTNode> read
	) {
		final PMTPath commonPath = PMTPath.findCommonPath(this.getKey(), key);
		switch (commonPath.whichRemainderIsLeft()) {
			case EXISTING:
				acc = handleOnlyExistingRemainderIsLeft(this, val, acc, commonPath, represent);
				break;
			case NEW, NONE:
				acc = handleNoRemainderIsLeft(this, val, acc, commonPath, represent, read);
				break;
			case EXISTING_AND_NEW:
				acc = handleBothExistingAndNewRemainders(this, val, acc, commonPath, represent);
				break;
			default:
				throw new IllegalStateException(String.format("Unexpected subtree: %s", commonPath.whichRemainderIsLeft()));
		}
		return acc;
	}

	private PMTAcc handleBothExistingAndNewRemainders(
			PMTNode current,
			byte[] val,
			PMTAcc acc,
			PMTPath commonPath,
			Function<PMTNode, byte[]> represent
	) {
		var remainderOld = commonPath.getRemainder(PMTPath.RemainingSubtree.EXISTING);
		PMTKey remainderNew = commonPath.getRemainder(PMTPath.RemainingSubtree.NEW);
		byte[] sliceVal;
		// INFO: as implemented here, the key-end can be empty
		var newLeaf = new PMTLeaf(remainderNew.getTailNibbles(), val);
		if (remainderOld.isNibble()) {
			sliceVal = current.getValue();
		} else {
			var newShorter = new PMTExt(remainderOld.getTailNibbles(), current.getValue());
			acc.add(newShorter);
			sliceVal = represent.apply(newShorter);
		}
		PMTBranch newBranch = new PMTBranch(
				null,
				new PMTBranch.PMTBranchChild(remainderNew.getFirstNibble(), represent.apply(newLeaf)),
				new PMTBranch.PMTBranchChild(remainderOld.getFirstNibble(), sliceVal)
		);
		acc = computeAndSetTip(commonPath, newBranch, acc, represent);
		acc.add(newBranch, newLeaf);
		acc.mark(current);
		return acc;
	}

	private PMTAcc handleNoRemainderIsLeft(
			PMTNode current,
			byte[] val,
			PMTAcc acc,
			PMTPath commonPath,
			Function<PMTNode, byte[]> represent,
			Function<byte[], PMTNode> read
	) {
		var newRemainder = commonPath.getRemainder(PMTPath.RemainingSubtree.NEW);
		var nextHash = current.getValue();
		var nextNode = read.apply(nextHash);
		// INFO: for NONE, the NEW will be empty
		acc = nextNode.insertNode(newRemainder, val, acc, represent, read);
		var subTip = acc.getTip();
		// INFO: for NEW or NONE, the commonPrefix can't be null as it existed here
		var newExt = new PMTExt(commonPath.getCommonPrefix(), represent.apply(subTip));
		acc.setTip(newExt);
		acc.add(newExt);
		acc.mark(current);
		return acc;
	}

	private PMTAcc handleOnlyExistingRemainderIsLeft(
			PMTNode current,
			byte[] val,
			PMTAcc acc,
			PMTPath commonPath,
			Function<PMTNode, byte[]> represent
	) {
		var remainder = commonPath.getRemainder(PMTPath.RemainingSubtree.EXISTING);
		byte[] sliceVal;
		if (remainder.isNibble()) {
			sliceVal = current.getValue();
		} else {
			var newShorter = new PMTExt(remainder.getTailNibbles(), current.getValue());
			acc.add(newShorter);
			sliceVal = represent.apply(newShorter);
		}
		var newBranch = new PMTBranch(
				val,
				new PMTBranch.PMTBranchChild(remainder.getFirstNibble(), sliceVal)
		);
		acc = computeAndSetTip(commonPath, newBranch, acc, represent);
		acc.add(newBranch);
		acc.mark(current);
		return acc;
	}

	public PMTAcc getValue(PMTKey key, PMTAcc acc, Function<byte[], PMTNode> read) {
		final PMTPath commonPath = PMTPath.findCommonPath(this.getKey(), key);
		switch (commonPath.whichRemainderIsLeft()) {
			case NEW, NONE:
				acc.mark(this);
				var nextHash = this.getValue();
				var nextNode = read.apply(nextHash);
				acc = nextNode.getValue(commonPath.getRemainder(PMTPath.RemainingSubtree.NEW), acc, read);
				break;
			case EXISTING_AND_NEW, EXISTING:
				acc.mark(this);
				acc.setNotFound();
				break;
			default:
				throw new IllegalStateException(String.format("Unexpected subtree: %s", commonPath.whichRemainderIsLeft()));
		}
		return acc;
	}
}
