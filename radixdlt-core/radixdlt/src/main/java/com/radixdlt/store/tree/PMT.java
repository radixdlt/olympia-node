package com.radixdlt.store.tree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;

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
	// re-check overlap logic after recursive call
	// common path method
	// serialization

	private HashMap<byte[], byte[]> localDb = new HashMap<>(); // mock for final persistent DB API
	private PMTNode root;

	public byte[] add(byte[] key, byte[] val) {

		try {
			var pmtKey = new PMTKey(key);

			// TODO: consider API: adding null -> remove
			if (this.root != null) {
				var newResult = insertNode(this.root, pmtKey, val);
				this.root = newResult.getTip();
			} else {
				// TODO: move to big case when it can handle nulls
				PMTNode nodeRoot = insertLeaf(pmtKey, val);
				this.root = nodeRoot;
			}
		}
		catch (Exception e) {
			log.error("Add failure: {} for: {} {} {}",
				e.getMessage(),
				TreeUtils.toHexString(key),
				TreeUtils.toHexString(val),
				TreeUtils.toHexString(root.getHash()));
		}
		// TODO: need a protocol for exceptions
		return this.root.getHash();
	}

	PMTResult insertNode(PMTNode above, PMTKey pmtKey, byte[] val) {
		return insertNode(above, pmtKey, val, new PMTResult());
	}

	PMTResult insertNode(PMTNode current, PMTKey pmtKey, byte[] val, PMTResult pmtResult) {
		pmtResult = this.findCommonPath(pmtResult, current, pmtKey);
		switch (current.getNodeType()) {
			case LEAF:
			 	switch (pmtResult.whichRemainderIsLeft()) {
			 		case OLD:
			 			var remainder = pmtResult.getRemainder(PMTResult.Subtree.OLD);
			 			var newLeaf = insertSubLeaf(remainder.getFirstNibble(), remainder.getTailNibbles(), val);
						var newBranch = insertBranch(current.getValue(), newLeaf);
						var newExt = insertExtension(pmtResult, newBranch);
						pmtResult.setTip(newExt == null ? newBranch : newExt);
						break;
					case NEW:
						remainder = pmtResult.getRemainder(PMTResult.Subtree.NEW);
						newLeaf = insertSubLeaf(remainder.getFirstNibble(), remainder.getTailNibbles(), current.value);
						newBranch = insertBranch(val, newLeaf);
						newExt = insertExtension(pmtResult, newBranch);
						pmtResult.setTip(newExt == null ? newBranch : newExt);
						break;
					case BOTH:
						var remainderNew = pmtResult.getRemainder(PMTResult.Subtree.NEW);
						var remainderOld = pmtResult.getRemainder(PMTResult.Subtree.OLD);
						var newLeafNew = insertSubLeaf(remainderNew.getFirstNibble(), remainderNew.getTailNibbles(), val);
						var newLeafOld = insertSubLeaf(remainderOld.getFirstNibble(), remainderOld.getTailNibbles(), current.value);
						newBranch = insertBranch(newLeafNew, newLeafOld);
						newExt = insertExtension(pmtResult, newBranch);
						pmtResult.setTip(newExt == null ? newBranch : newExt);
						break;
					case NONE:
					    if (val == current.getValue()) {
					    	throw new IllegalArgumentException("Nothing changed");
						} else {
					    	// INFO: we preserve whole key-end as there are no branches
					    	newLeaf = insertLeaf(pmtKey, val);
							pmtResult.setTip(newLeaf);
						}
						break;
			}
			break;
			case BRANCH:
				// INFO: Existing branch has empty key and remainder
				var currentBranch = (PMTBranch) current;
				switch (pmtResult.whichRemainderIsLeft()) {
					case NONE:
						var modifiedBranch = currentBranch.setValue(val);
						var savedBranch = insertBranch(modifiedBranch);
						pmtResult.setTip(savedBranch);
						break;
					case NEW:
						var nextHash = currentBranch.getNextHash(pmtKey);
						var remainder = pmtResult.getRemainder(PMTResult.Subtree.NEW);
						PMTNode nextTip;
						if (nextHash == null) {
							nextTip = insertSubLeaf(pmtKey.getFirstNibble(), pmtKey.getTailNibbles(), val);
						} else {
							var nextNode = read(nextHash);
							var pmtResultNext = insertNode(nextNode, remainder.getTailNibbles(), val, pmtResult);
							nextTip = pmtResultNext.getTip();
							// INTO: Only here we have a context of a position-nibble
							nextTip.setFirstNibble(remainder.getFirstNibble());
						}
						var branchWithNext = currentBranch.setNibble(nextTip);
						savedBranch = insertBranch(branchWithNext);
						pmtResult.setTip(savedBranch);
						break;
				}
				break;
			case EXTENSION:
				switch (pmtResult.whichRemainderIsLeft()) {
					case OLD:
						var remainder = pmtResult.getRemainder(PMTResult.Subtree.OLD);
						var newShorter = splitExtension(remainder, current);
						var newBranch = insertBranch(val, newShorter);
						var newExt = insertExtension(pmtResult, newBranch);
						pmtResult.setTip(newExt == null ? newBranch : newExt);
						break;
					case NEW:
					case NONE:
						var nextHash = current.getValue();
						var nextNode = read(nextHash);
						// INFO: for NONE, the NEW will be empty
						var pmtResultNext = insertNode(nextNode, pmtResult.getRemainder(PMTResult.Subtree.NEW), val, pmtResult);
						pmtResult.setTip(insertExtension(pmtResult, (PMTBranch) pmtResultNext.getTip()));
						break;
					case BOTH:
						var remainderNew = pmtResult.getRemainder(PMTResult.Subtree.NEW);
						var remainderOld = pmtResult.getRemainder(PMTResult.Subtree.OLD);
						var newLeaf = insertSubLeaf(remainderNew.getFirstNibble(), remainderNew.getTailNibbles(), val);
						newShorter = splitExtension(remainderOld, current);
						newBranch = insertBranch(newLeaf, newShorter);
						newExt = insertExtension(pmtResult, newBranch);
						pmtResult.setTip(newExt == null ? newBranch : newExt);
						break;
				}
				break;
		}
		return pmtResult.cleanup();
	}

	// INFO: the only case when leaf has full key in key-end
	PMTLeaf insertLeaf(PMTKey key, byte[] value) {
		var newNode = new PMTLeaf(key, value);
		save(newNode);
		return newNode;
	}

	PMTLeaf insertSubLeaf(PMTKey first, PMTKey tail, byte[] value) {
		var newNode = new PMTLeaf(first, tail, value);
		save(newNode);
		return newNode;
	}


	PMTBranch insertBranch(PMTBranch modifiedBranch) {
		save(modifiedBranch);
		return modifiedBranch;
	}

	PMTBranch insertBranch(PMTNode... nextNode) {
		return insertBranch(null, nextNode);
	}

	PMTBranch insertBranch(byte[] branchValue, PMTNode... nextNode) {
		var newBranch = new PMTBranch(branchValue, nextNode);
		save(newBranch);
		return newBranch;
	}

	// INFO: After branch
	PMTExt splitExtension(PMTKey remainder, PMTNode current) {
		if (remainder.isNibble()) {
			// INFO: The remainder will be expressed as position in the branch only
			//       * it's not necessarily a leaf as it has hash pointer to another branch
			//       * hash pointer will be rewritten to nibble position in a branch
			//       * this is why we dont save it as it's only container to pass the first nibble to branch
			return new PMTExt(remainder.getFirstNibble(), remainder.getTailNibbles(), current.getValue());
		} else {
			var newExtension = new PMTExt(remainder.getFirstNibble(), remainder.getTailNibbles(), current.getValue());
			save(newExtension);
			return newExtension;
		}
	}

	// INFO: before branch
	PMTExt insertExtension(PMTResult pmtResult, PMTBranch branch) {
		if (pmtResult.getCommonPrefix().isEmpty()) {
			return null;
		} else {
			var newExtension = new PMTExt(pmtResult.getCommonPrefix(), branch.getHash());
			save(newExtension);
			return newExtension;
		}
	}

	private void save(PMTNode node) {
		var ser = node.serialize();
		if (ser.length >= PMTNode.DB_SIZE_COND) {
			this.localDb.put(node.getHash(), ser);
		}
	}

	private PMTNode read(byte[] hash) {
		if (hash.length < PMTNode.DB_SIZE_COND) {
			return PMTNode.deserialize(hash);
		} else {
			var node = this.localDb.get(hash);
			// TODO: this will return leaf or ext or branch
			//       shall we cast in case or in deserializer?
			return PMTNode.deserialize(node);
		}
	}

	PMTResult findCommonPath(PMTResult result, PMTNode top, PMTKey key) {
		// move to PMTKEY?
		return result;
	}

	PMTResult remove(PMTKey pmtKey) {
		// TODO
		return null;
	}

	PMTResult removeExtensionNode(PMTNode pmtNode) {
		// TODO
		return null;
	}

	PMTResult removeLeafNode(PMTNode pmtNode) {
		// TODO
		return null;
	}

	PMTResult removeBranchNode(PMTNode pmtNode) {
		// TODO
		return null;
	}
}
