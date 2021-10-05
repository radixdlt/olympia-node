package com.radixdlt.store.tree;

import java.util.HashMap;

public class PMT {

	// API:
	//add
	//update
	//delete
	//get
	//
	//get_root
	//get_proof
	//verify_proof

	// INTEGRATION PLAN:
	// 1. Run it in parallel to db saves
	// 2. Introduce config for enable/disable
	// 3. Introduce config for GC
	// 3. Write consistency tests
	// 4. Take over storage of UPs and DOWNs

	// XXX For retired nodes put the epoch number when they were valid (for precise GC)
	// XXX Make it configurable -- deletion of old nodes
	// XXX epoch number -> epoch when it becomes DOWN

	// XXX What will be strategy for DOWN vs UP? Shall we keep both vs deleting DOWN? Config...


	private HashMap<byte[], PMTNode> localDb = new HashMap<byte[], PMTNode>(); // mock for final persistent DB API
	private PMTNode root;

	public byte[] add(byte[] key, byte[] val) {

		var pmtKey = new PMTKey(key);

		// consider adding val = null -> remove
		if (root != null) {
			insertNode(this.root, pmtKey, val);

		} else {
			PMTNode nodeRoot = insertLeaf(pmtKey, val);
			this.root = nodeRoot;
		}

		return this.root.getHash();
	}

	PMTResult insertNode(PMTNode above, PMTKey pmtKey, byte[] val) {
		return insertNode(above, pmtKey, val, new PMTResult());
	}

	PMTResult insertNode(PMTNode above, PMTKey pmtKey, byte[] val, PMTResult pmtResult) {
		switch (above.getNodeType()) {
			case LEAF:
				pmtResult = this.findCommonPath(pmtResult, above, pmtKey);
			 	switch (pmtResult.whichRemainderIsLeft()) {
			 		case OLD:
			 			var remainder = pmtResult.getRemainder(PMTResult.Subtree.OLD);
			 			var newLeaf = insertLeaf(remainder, val);
						var newBranch = insertBranch(newLeaf, above.getValue());
						var newExt = insertExtension(pmtResult, newBranch);
						pmtResult.setTip(newExt == null ? newBranch.getHash() : newExt.getHash());
						break;
					case NEW:
						// leaf
						// branch
						// extension check
						break;
					case BOTH:
						// leaf
						// leaf
						// branch
						// extension check
						break;
					case NONE:
					    // nothing changes
						break;
			}
			break;
			case BRANCH:
				// IF
				// key matches branch key/level
				// branch value = value
				// ELSE
				// read next node based on common nibble
				// insertNode with PathRemainder
				// when back set value of the insertedNode in the good position in the branch
				break;
			case EXTENSION:
				pmtResult = this.findCommonPath(pmtResult, above, pmtKey);
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

	PMTBranch insertBranch(PMTLeaf leafNode, byte[] branchValue) {
		var newBranch = new PMTBranch(leafNode, branchValue);
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
		// TODO what's the best way to keep it in the db. Fully serialized?
		this.localDb.put(node.getHash(), node);
	}

	private PMTNode get(byte[] hash) {
		// TODO returning full node for now
		return this.localDb.get(hash);
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
