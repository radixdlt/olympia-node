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
	// check overlap logic after recursive call
	// setTip + cleanup of pmtResult
	// branch if else
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
				PMTNode nodeRoot = insertFirst(pmtKey, val);
				this.root = nodeRoot;
			}
		}
		catch (IllegalArgumentException e) {
			log.error("Add failure: {} {}", TreeUtils.toHexString(key), e.getMessage());
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
			 			var newLeaf = insertLeaf(remainder, val);
						var newBranch = insertBranch(current.getValue(), newLeaf);
						var newExt = insertExtension(pmtResult, newBranch);
						pmtResult.setTip(newExt == null ? newBranch : newExt);
						break;
					case NEW:
						remainder = pmtResult.getRemainder(PMTResult.Subtree.NEW);
						newLeaf = insertLeaf(remainder, current.value);
						newBranch = insertBranch(val, newLeaf);
						newExt = insertExtension(pmtResult, newBranch);
						pmtResult.setTip(newExt == null ? newBranch : newExt);
						break;
					case BOTH:
						var remainderNew = pmtResult.getRemainder(PMTResult.Subtree.NEW);
						var remainderOld = pmtResult.getRemainder(PMTResult.Subtree.OLD);
						var newLeafNew = insertLeaf(remainderNew, val);
						var newLeafOld = insertLeaf(remainderOld, current.value);
						newBranch = insertBranch(newLeafNew, newLeafOld);
						newExt = insertExtension(pmtResult, newBranch);
						pmtResult.setTip(newExt == null ? newBranch : newExt);
						break;
					case NONE:
					    if (val == current.getValue()) {
					    	throw new IllegalArgumentException("Nothing changed");
						} else {
					    	newLeaf = insertLeaf(pmtKey, val);
							pmtResult.setTip(newLeaf);
						}
						break;
			}
			break;
			case BRANCH:
				// extract to pmtRESULT and use enum for clarity
				var currentBranch = (PMTBranch) current;
				if (pmtKey.isEmpty()) {
					if (currentBranch.getValue() == val) {
						throw new IllegalArgumentException("Nothing changed");
					} else {
						var modifiedBranch = currentBranch.setValue(val);
						var savedBranch = insertBranch(modifiedBranch);
						pmtResult.setTip(savedBranch);
					}
				} else {
					var nextHash = currentBranch.getNextHash(pmtKey);
					var nextNode = read(nextHash);
					var pmtResultNext = insertNode(nextNode, pmtResult.getRemainder(PMTResult.Subtree.NEW), val, pmtResult);
					var branchWithNext = currentBranch.setNibble(pmtResultNext.getTip());
					var savedBranch = insertBranch(branchWithNext);
					pmtResult.setTip(savedBranch);
				}
				break;
			case EXTENSION:
				switch (pmtResult.whichRemainderIsLeft()) {
					case OLD:
						var newShorter = splitExtension(pmtResult, current);
						var newBranch = insertBranch(val, newShorter);
						var newExt = insertExtension(pmtResult, newBranch);
						pmtResult.setTip(newExt == null ? newBranch : newExt);
						break;
					case NEW:
					case NONE:
						var nextHash = current.getValue();
						var nextNode = read(nextHash);
						var pmtResultNext = insertNode(nextNode, pmtResult.getRemainder(PMTResult.Subtree.NEW), val, pmtResult);
						pmtResult.setTip(insertExtension(pmtResult, (PMTBranch) pmtResultNext.getTip()));
						break;
					case BOTH:
						var remainder = pmtResult.getRemainder(PMTResult.Subtree.NEW);
						var newLeaf = insertLeaf(remainder, val);
						newShorter = splitExtension(pmtResult, current);
						newBranch = insertBranch(newLeaf, newShorter);
						newExt = insertExtension(pmtResult, newBranch);
						pmtResult.setTip(newExt == null ? newBranch : newExt);
						break;
				}
				break;
		}
		return pmtResult;
	}

	PMTLeaf insertFirst(PMTKey pmtKey, byte[] value) {
		var newNode = new PMTLeaf(pmtKey, value, PMTNode.BEFORE_BRANCH);
		save(newNode);
		return newNode;
	}

	PMTLeaf insertLeaf(PMTKey pmtKey, byte[] value) {
		var newNode = new PMTLeaf(pmtKey, value, PMTNode.AFTER_BRANCH);
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
	PMTExt splitExtension(PMTResult pmtResult, PMTNode current) {
		var remainder = pmtResult.getRemainder(PMTResult.Subtree.OLD);
		if (remainder.isNibble()) {
			// INFO: The key will be position in the branch only
			//       * it's not necessarily a leaf as it has hash pointer to another branch
			//       * hash pointer will be rewritten to nibble position in a branch
			return new PMTExt(remainder, current.getValue(), PMTNode.AFTER_BRANCH);
		} else {
			var newExtension = new PMTExt(remainder, current.getValue(), PMTNode.AFTER_BRANCH);
			save(newExtension);
			return newExtension;
		}
	}

	// INFO: before branch
	PMTExt insertExtension(PMTResult pmtResult, PMTBranch branch) {
		if (pmtResult.getCommonPrefix().isEmpty()) {
			// TODO: meh...
			return null;
		} else {
			var newExtension = new PMTExt(pmtResult.getCommonPrefix(), branch.getHash(), PMTNode.BEFORE_BRANCH);
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
