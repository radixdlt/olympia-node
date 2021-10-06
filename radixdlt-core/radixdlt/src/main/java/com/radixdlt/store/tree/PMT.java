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

	// MAIN IMPLEMENTATION TODOs
	// 1. What to do with serialization for hashing? RLP? What about node serialization for db.
	// 2. Use Optional, Result through out the stack
	// 3. What's concurrency approach
	// 4. Compression of UP/DOWN, tree split for different kinds
	// 5. Config matrix and background sync
	// 6. array copy issue

	// INTEGRATION PLAN:
	// 1. Run it in parallel to db saves
	// 2. Introduce config for enable/disable
	// 3. Introduce strategy for GC (no GC = null DOWN, GC DOWN, GC DOWN after N)
	// 3.1. ^^ this would require an N going along serialized node to the db
	// 4. Measure growth, io and latency
	// 5. Write consistency tests
	// 6. Take over storage of UPs and DOWNs

	// XXX For retired nodes put the epoch number when they were valid (for precise GC)
	// XXX Make it configurable -- deletion of old nodes
	// XXX epoch number -> epoch when it becomes DOWN
	// XXX What will be strategy for DOWN vs UP? Shall we keep both vs deleting DOWN? Config...


	private HashMap<byte[], byte[]> localDb = new HashMap<>(); // mock for final persistent DB API
	private PMTNode root;

	public byte[] add(byte[] key, byte[] val) {

		try {
			var pmtKey = new PMTKey(key);

			// TODO: consider adding val = null -> remove
			if (this.root != null) {
				var newResult = insertNode(this.root, pmtKey, val);
				this.root = newResult.getTip();
			} else {
				PMTNode nodeRoot = insertLeaf(pmtKey, val);
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
				pmtResult = this.findCommonPath(pmtResult, current, pmtKey);
				switch (pmtResult.whichRemainderIsLeft()) {
					case OLD:

						// change extension (shorten)
						// create a branch
						// extension check
						break;
					case NEW:

						// insertNode with next Node (with new key)
						// extension update  (as branch below changes hash)
						break;
					case BOTH:

						// change extension (shorten)
						// create a branch
						// extension check
						break;
					case NONE:
						// just like NEW, but going to branch as the new Val will be set to branch Val
						// so:
						// insertNode (^^)
						// extension update (as branch below changes hash)
						break;
				}
				break;
			case EMPTY:
				// leaf
				break;
		}
		return pmtResult;
	}

	PMTLeaf insertLeaf(PMTKey pmtKey, byte[] value) {
		var newNode = new PMTLeaf(pmtKey, value);
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
		this.localDb.put(node.getHash(), node.serialize());
	}

	private PMTNode read(byte[] hash) {
		if (hash.length < PMTNode.HASH_COND) {
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

	PMTResult applyRules(PMTResult inProgress) {
		// TODO
		return null;
	}
}
