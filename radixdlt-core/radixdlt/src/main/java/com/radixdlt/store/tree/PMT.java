package com.radixdlt.store.tree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.stream.Collectors;

public class PMT {

	private static final Logger log = LogManager.getLogger();

	// API:
	//add
	//update
	//delete
	//get
	//
	//get_root
	//get_proof
	//verify_proof

	// NEXT:
	// TODO: * kick out branch Nibble from Ext and Leaf objects? (they are only for transfer to branch update)
	//       * simplify getFirstNibble
	//       * simplify getKey.getKey (from PMTNode -> PMTKey -> int[])
	// serialization

	public PMT(PMTStorage db) {
		this.db = db;
	}

	private PMTStorage db;
	private PMTNode root;

	public byte[] add(byte[] key, byte[] val) {

		try {
			var pmtKey = new PMTKey(PMTPath.intoNibbles(key));

			if (this.root != null) {
				var acc = insertNode(this.root, pmtKey, val, new PMTAcc());
				var sanitizedAcc = acc.getNewNodes().stream().filter(Objects::nonNull).collect(Collectors.toList());
				db.save(sanitizedAcc);
				this.root = acc.getTip();
			} else {
				PMTNode nodeRoot = new PMTLeaf(pmtKey, val);
				this.root = nodeRoot;
			}
		}
		catch (Exception e) {
			log.error("PMT operation failure: {} for: {} {} {}",
				e.getMessage(),
				TreeUtils.toHexString(key),
				TreeUtils.toHexString(val),
				TreeUtils.toHexString(root == null ? null : root.getHash()));
		}
		return root == null ? null : root.getHash();
	}

	PMTAcc insertNode(PMTNode current, PMTKey key, byte[] val, PMTAcc acc) {
		final PMTPath commonPath = PMTPath.findCommonPath(current.getKey(), key);
		switch (current.getNodeType()) {
			case LEAF:
			 	switch (commonPath.whichRemainderIsLeft()) {
					case OLD:
						var remainder = commonPath.getRemainder(PMTPath.Subtree.OLD);
						var newLeaf = new PMTLeaf(remainder.getFirstNibble(), remainder.getTailNibbles(), val);
						var newBranch = new PMTBranch(current.getValue(), newLeaf);
						var newExt = insertExtension(commonPath, newBranch);
						acc.newTip = newExt == null ? newBranch : newExt;
						acc.add(newLeaf, newBranch, newExt);
						acc.mark(current);
						break;
					case NEW:
						remainder = commonPath.getRemainder(PMTPath.Subtree.NEW);
						newLeaf = new PMTLeaf(remainder.getFirstNibble(), remainder.getTailNibbles(), current.value);
						newBranch = new PMTBranch(val, newLeaf);
						newExt = insertExtension(commonPath, newBranch);
						acc.newTip = newExt == null ? newBranch : newExt;
						acc.add(newLeaf, newBranch, newExt);
						acc.mark(current);
						break;
					case BOTH:
						var remainderNew = commonPath.getRemainder(PMTPath.Subtree.NEW);
						var remainderOld = commonPath.getRemainder(PMTPath.Subtree.OLD);
						var newLeafNew = new PMTLeaf(remainderNew.getFirstNibble(), remainderNew.getTailNibbles(), val);
						var newLeafOld = new PMTLeaf(remainderOld.getFirstNibble(), remainderOld.getTailNibbles(), current.value);
						newBranch = new PMTBranch(null, newLeafNew, newLeafOld);
						newExt = insertExtension(commonPath, newBranch);
						acc.newTip = newExt == null ? newBranch : newExt;
						acc.add(newLeafNew, newLeafOld, newBranch, newExt);
						acc.mark(current);
						break;
					case NONE:
						if (val == current.getValue()) {
							throw new IllegalArgumentException("Nothing changed");
						} else {
							// INFO: we preserve whole key-end as there are no branches
							newLeaf = new PMTLeaf(key, val);
							acc.newTip = newLeaf;
							acc.add(newLeaf);
							acc.mark(current);
						}
						break;
				}
				break;
			case BRANCH:
				// INFO: OLD branch always has empty key and remainder
				var currentBranch = (PMTBranch) current;
				switch (commonPath.whichRemainderIsLeft()) {
					case NONE:
						var modifiedBranch = cloneBranch(currentBranch);
						modifiedBranch.setValue(val);
						acc.newTip = modifiedBranch;
						acc.add(modifiedBranch);
						acc.mark(current);
						break;
					case NEW:
						var nextHash = currentBranch.getNextHash(key);
						PMTNode subTip;
						if (nextHash == null) {
							var newLeaf = new PMTLeaf(key.getFirstNibble(), key.getTailNibbles(), val);
							acc.add(newLeaf);
							subTip = newLeaf;
						} else {
							var nextNode = read(nextHash);
							subTip = insertNode(nextNode, key.getTailNibbles(), val, acc).getTip();
						}
						var branchWithNext = cloneBranch(currentBranch);
						branchWithNext.setNibble(key.getFirstNibble(), subTip);
						acc.newTip = branchWithNext;
						acc.add(branchWithNext);
						acc.mark(current);
						break;
				}
				break;
			case EXTENSION:
				switch (commonPath.whichRemainderIsLeft()) {
					case OLD:
						var remainder = commonPath.getRemainder(PMTPath.Subtree.OLD);
						var newShorter = splitExtension(remainder, current, acc);
						var newBranch = new PMTBranch(val, newShorter);
						var newExt = insertExtension(commonPath, newBranch);
						acc.newTip = newExt == null ? newBranch : newExt;
						acc.add(newBranch, newExt);
						acc.mark(current);
						break;
					case NEW:
					case NONE:
						var nextHash = current.getValue();
						var nextNode = read(nextHash);
						// INFO: for NONE, the NEW will be empty
						var subTip = insertNode(nextNode, commonPath.getRemainder(PMTPath.Subtree.NEW), val, acc).getTip();
						// INFO: for NEW or NONE, the commonPrefix can't be null as it existed here
						newExt = new PMTExt(commonPath.getCommonPrefix(), subTip.getHash());
						acc.newTip = newExt;
						acc.add(newExt);
						acc.mark(current);
						break;
					case BOTH:
						var remainderNew = commonPath.getRemainder(PMTPath.Subtree.NEW);
						var remainderOld = commonPath.getRemainder(PMTPath.Subtree.OLD);
						var newLeaf = new PMTLeaf(remainderNew.getFirstNibble(), remainderNew.getTailNibbles(), val);
						newShorter = splitExtension(remainderOld, current, acc);
						newBranch = new PMTBranch(null, newLeaf, newShorter);
						newExt = insertExtension(commonPath, newBranch);
						acc.newTip = newExt == null ? newBranch : newExt;
						acc.add(newShorter, newBranch, newExt, newLeaf);
						// INFO: the value of current Ext is rewritten to newShorter, so that node is intact
						acc.mark(current);
						break;
				}
				break;
		}
		return acc;
	}

	PMTExt insertExtension(PMTPath pmtPath, PMTBranch branch) {
		if (pmtPath.getCommonPrefix().isEmpty()) {
			return null;
		} else {
			return new PMTExt(pmtPath.getCommonPrefix(), branch.getHash());
		}
	}

	PMTBranch cloneBranch(PMTBranch currentBranch) {
		return currentBranch.copyForEdit();
	}

	PMTExt splitExtension(PMTKey remainder, PMTNode current, PMTAcc acc) {
		if (remainder.isNibble()) {
			// INFO: The remainder will be ONLY expressed as position in the branch
			//       * it's not necessarily a leaf as it has hash pointer to another branch
			//       * hash pointer will be rewritten to nibble position in a branch
			//       * this is why we dont save it as it's only container to pass the first nibble to branch
			return new PMTExt(remainder.getFirstNibble(), remainder.getTailNibbles(), current.getValue());
		} else {
			var newShorter = new PMTExt(remainder.getFirstNibble(), remainder.getTailNibbles(), current.getValue());
			acc.add(newShorter);
			return newShorter;
		}
	}

	private PMTNode read(byte[] hash) {
		return db.read(hash);
	}
}
