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
	// common path method
	// serialization

	private HashMap<byte[], byte[]> localDb = new HashMap<>(); // mock for final persistent DB API
	private PMTNode root;

	public byte[] add(byte[] key, byte[] val) {

		try {
			var pmtKey = new PMTKey(key);

			// TODO: consider API: adding null -> remove
			if (this.root != null) {
				this.root = insertNode(this.root, pmtKey, val);
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

	PMTNode insertNode(PMTNode current, PMTKey key, byte[] val) {
		final PMTPath commonPath = this.findCommonPath(current, key);
		PMTNode newTip = null;
		switch (current.getNodeType()) {
			case LEAF:
			 	switch (commonPath.whichRemainderIsLeft()) {
			 		case OLD:
			 			var remainder = commonPath.getRemainder(PMTPath.Subtree.OLD);
			 			var newLeaf = insertSubLeaf(remainder.getFirstNibble(), remainder.getTailNibbles(), val);
						var newBranch = insertBranch(current.getValue(), newLeaf);
						var newExt = insertExtension(commonPath, newBranch);
						newTip = newExt == null ? newBranch : newExt;
						break;
					case NEW:
						remainder = commonPath.getRemainder(PMTPath.Subtree.NEW);
						newLeaf = insertSubLeaf(remainder.getFirstNibble(), remainder.getTailNibbles(), current.value);
						newBranch = insertBranch(val, newLeaf);
						newExt = insertExtension(commonPath, newBranch);
						newTip = newExt == null ? newBranch : newExt;
						break;
					case BOTH:
						var remainderNew = commonPath.getRemainder(PMTPath.Subtree.NEW);
						var remainderOld = commonPath.getRemainder(PMTPath.Subtree.OLD);
						var newLeafNew = insertSubLeaf(remainderNew.getFirstNibble(), remainderNew.getTailNibbles(), val);
						var newLeafOld = insertSubLeaf(remainderOld.getFirstNibble(), remainderOld.getTailNibbles(), current.value);
						newBranch = insertBranch(newLeafNew, newLeafOld);
						newExt = insertExtension(commonPath, newBranch);
						newTip = newExt == null ? newBranch : newExt;
						break;
					case NONE:
					    if (val == current.getValue()) {
					    	throw new IllegalArgumentException("Nothing changed");
						} else {
					    	// INFO: we preserve whole key-end as there are no branches
							newTip = insertLeaf(key, val);
						}
						break;
			}
			break;
			case BRANCH:
				// INFO: OLD branch always has empty key and remainder
				var currentBranch = (PMTBranch) current;
				switch (commonPath.whichRemainderIsLeft()) {
					case NONE:
						var modifiedBranch = currentBranch.setValue(val);
						newTip = insertBranch(modifiedBranch); // TODO: for mem cache we will need work on copy here
						break;
					case NEW:
						var nextHash = currentBranch.getNextHash(key);
						PMTNode subTip;
						if (nextHash == null) {
							subTip = insertSubLeaf(key.getFirstNibble(), key.getTailNibbles(), val);
						} else {
							var nextNode = read(nextHash);
							subTip = insertNode(nextNode, key.getTailNibbles(), val);
						}
						var branchWithNext = currentBranch.setNibble(key.getFirstNibble(), subTip);
						newTip = insertBranch(branchWithNext); // TODO: for mem cache we will need work on copy here
						break;
				}
				break;
			case EXTENSION:
				switch (commonPath.whichRemainderIsLeft()) {
					case OLD:
						var remainder = commonPath.getRemainder(PMTPath.Subtree.OLD);
						var newShorter = splitExtension(remainder, current);
						var newBranch = insertBranch(val, newShorter);
						var newExt = insertExtension(commonPath, newBranch);
						newTip = newExt == null ? newBranch : newExt;
						break;
					case NEW:
					case NONE:
						var nextHash = current.getValue();
						var nextNode = read(nextHash);
						// INFO: for NONE, the NEW will be empty
						var subTip = insertNode(nextNode, commonPath.getRemainder(PMTPath.Subtree.NEW), val);
						newTip = insertExtension(commonPath, (PMTBranch) subTip);
						break;
					case BOTH:
						var remainderNew = commonPath.getRemainder(PMTPath.Subtree.NEW);
						var remainderOld = commonPath.getRemainder(PMTPath.Subtree.OLD);
						var newLeaf = insertSubLeaf(remainderNew.getFirstNibble(), remainderNew.getTailNibbles(), val);
						newShorter = splitExtension(remainderOld, current);
						newBranch = insertBranch(newLeaf, newShorter);
						newExt = insertExtension(commonPath, newBranch);
						newTip = newExt == null ? newBranch : newExt;
						break;
				}
				break;
		}
		return newTip;
	}

	// INFO: the only case when leaf has full key in key-end
	PMTLeaf insertLeaf(PMTKey key, byte[] value) {
		var newNode = new PMTLeaf(key, value);
		save(newNode);
		return newNode;
	}

	PMTLeaf insertSubLeaf(PMTKey branchNibble, PMTKey keyNibbles, byte[] value) {
		var newNode = new PMTLeaf(branchNibble, keyNibbles, value);
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
	PMTExt insertExtension(PMTPath pmtPath, PMTBranch branch) {
		if (pmtPath.getCommonPrefix().isEmpty()) {
			return null;
		} else {
			var newExtension = new PMTExt(pmtPath.getCommonPrefix(), branch.getHash());
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

	PMTPath findCommonPath(PMTNode top, PMTKey key) {
		// move to PMTKEY?
		return null;
	}

	PMTPath remove(PMTKey pmtKey) {
		// TODO
		return null;
	}

	PMTPath removeExtensionNode(PMTNode pmtNode) {
		// TODO
		return null;
	}

	PMTPath removeLeafNode(PMTNode pmtNode) {
		// TODO
		return null;
	}

	PMTPath removeBranchNode(PMTNode pmtNode) {
		// TODO
		return null;
	}
}
