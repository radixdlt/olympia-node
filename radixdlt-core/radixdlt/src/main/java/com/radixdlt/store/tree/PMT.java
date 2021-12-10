package com.radixdlt.store.tree;

import com.radixdlt.store.tree.hash.HashFunction;
import com.radixdlt.store.tree.hash.SHA256;
import com.radixdlt.store.tree.storage.PMTCache;
import com.radixdlt.store.tree.storage.PMTStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public class PMT {

	private static final Logger log = LogManager.getLogger();
	public static final int CACHE_MAXIMUM_SIZE = 1000;

	private final PMTStorage db;
	private final PMTCache cache;
	private final HashFunction hashFunction;
	private PMTNode root;

	// API:
	//add
	//update
	//delete
	//get
	//
	//get_root
	//get_proof
	//verify_proof

	public PMT(PMTStorage db) {
		this(db, new SHA256(), Duration.of(10, ChronoUnit.MINUTES));
	}

	public PMT(PMTStorage db, HashFunction hashFunction, Duration cacheExpiryAfter) {
		this.db = db;
		this.cache = new PMTCache(
			CACHE_MAXIMUM_SIZE,
			cacheExpiryAfter,
			this::nodeLoader
		);
		this.hashFunction = hashFunction;
	}

	public byte[] represent(PMTNode node) {
		return represent(node, this.hashFunction);
	}

	public static byte[] represent(PMTNode node, HashFunction hashFunction) {
		var ser = node.serialize();
		if (hasDbRepresentation(ser)) {
			return hashFunction.hash(ser);
		} else {
			return ser;
		}
	}

	public static boolean hasDbRepresentation(byte[] serializedNode) {
		// INFO: the DB_SIZE_CONDITION relates to hash size
		return serializedNode.length >= PMTNode.DB_SIZE_COND;
	}

	public byte[] get(byte[] key) {
		var pmtKey = new PMTKey(PMTPath.intoNibbles(key));

		if (this.root != null) {
			var acc = getValue(this.root, pmtKey, new PMTAcc());
			if (acc.notFound()) {
				log.debug("Not found key: {} for root: {}",
					TreeUtils.toHexString(key),
					TreeUtils.toHexString(root == null ? null : represent(root)));
				// TODO XXX: what shall we return for not found? Maybe lets wrap everything in Option?
				return new byte[0];
			} else {
				return acc.getRetVal().getValue();
			}
		} else {
			log.debug("Tree empty when key: {}",
				TreeUtils.toHexString(key));
			// TODO XXX: what shall we return for empty Maybe lets wrap everything in Option?
			return null;
		}
	}

	public byte[] add(byte[] key, byte[] val) {

		try {
			var pmtKey = new PMTKey(PMTPath.intoNibbles(key));

			if (this.root != null) {
				var acc = insertNode(this.root, pmtKey, val, new PMTAcc());
				this.root = acc.getTip();
				acc.getNewNodes().stream()
					.filter(Objects::nonNull)
					.forEach(sanitizedAcc -> {
						this.cache.put(represent(sanitizedAcc), sanitizedAcc);
						byte[] serialisedNode = sanitizedAcc.serialize();
						if (sanitizedAcc == this.root || hasDbRepresentation(serialisedNode)) {
							this.db.save(hash(serialisedNode), serialisedNode);
						}
					});
			} else {
				PMTNode nodeRoot = new PMTLeaf(pmtKey, val);
				this.root = nodeRoot;
				byte[] serialisedNode = nodeRoot.serialize();
				this.cache.put(represent(nodeRoot), nodeRoot);
				this.db.save(hash(nodeRoot), serialisedNode);
			}
		} catch (Exception e) {
			log.error("PMT operation failure: {} for: {} {} {}",
				e.getMessage(),
				TreeUtils.toHexString(key),
				TreeUtils.toHexString(val),
				TreeUtils.toHexString(root == null ? null : hash(root)));
			throw e;
		}
		return root == null ? null : hash(root);
	}

	PMTAcc getValue(PMTNode current, PMTKey key, PMTAcc acc) {
		final PMTPath commonPath = PMTPath.findCommonPath(current.getKey(), key);
		switch (current.getNodeType()) {
			case LEAF:
				switch (commonPath.whichRemainderIsLeft()) {
					case NONE:
						acc.setRetVal(current);
						break;
					case NEW:
					case OLD:
					case BOTH:
						// INFO: not throwing exception as this tree is for flexible tree key length
						acc.mark(current);
						acc.setNotFound();
						break;
				}
				break;
			case BRANCH:
				var currentBranch = (PMTBranch) current;
				switch (commonPath.whichRemainderIsLeft()) {
					case NONE:
						acc.setRetVal(current);
						break;
					case NEW:
						acc.mark(current);
						var nextHash = currentBranch.getNextHash(key);
						if (nextHash == null) {
							acc.setNotFound();
						} else {
							var nextNode = read(nextHash);
							acc = getValue(nextNode, key.getTailNibbles(), acc);
						}
						break;
				}
				//INFO: ^^ Branch doesn't have a key, so there must not be OLD nor BOTH
				break;
			case EXTENSION:
				switch (commonPath.whichRemainderIsLeft()) {
					case NEW:
					case NONE:
						acc.mark(current);
						var nextHash = current.getValue();
						var nextNode = read(nextHash);
						acc = getValue(nextNode, commonPath.getRemainder(PMTPath.Subtree.NEW), acc);
						break;
					case BOTH:
					case OLD:
						acc.mark(current);
						acc.setNotFound();
						break;
				}
				break;
		}
		return acc;
	}

	PMTAcc insertNode(PMTNode current, PMTKey key, byte[] val, PMTAcc acc) {
		final PMTPath commonPath = PMTPath.findCommonPath(current.getKey(), key);
		switch (current.getNodeType()) {
			case LEAF:
			 	switch (commonPath.whichRemainderIsLeft()) {
					case OLD:
						var remainder = commonPath.getRemainder(PMTPath.Subtree.OLD);
						var newLeaf = new PMTLeaf(remainder.getTailNibbles(), current.getValue());
						var newBranch = new PMTBranch(
								val,
								new PMTBranch.PMTBranchChild(remainder.getFirstNibble(), represent(newLeaf))
						);
						acc = computeAndSetTip(commonPath, newBranch, acc);
						acc.add(newLeaf, newBranch);
						acc.mark(current);
						break;
					case NEW:
						remainder = commonPath.getRemainder(PMTPath.Subtree.NEW);
						newLeaf = new PMTLeaf(remainder.getTailNibbles(), val);
						newBranch = new PMTBranch(
								current.getValue(),
								new PMTBranch.PMTBranchChild(remainder.getFirstNibble(), represent(newLeaf))
						);
						acc = computeAndSetTip(commonPath, newBranch, acc);
						acc.add(newLeaf, newBranch);
						acc.mark(current);
						break;
					case BOTH:
						var remainderNew = commonPath.getRemainder(PMTPath.Subtree.NEW);
						var remainderOld = commonPath.getRemainder(PMTPath.Subtree.OLD);
						var newLeafNew = new PMTLeaf(remainderNew.getTailNibbles(), val);
						var newLeafOld = new PMTLeaf(remainderOld.getTailNibbles(), current.value);
						newBranch = new PMTBranch(
								null,
								new PMTBranch.PMTBranchChild(remainderNew.getFirstNibble(), represent(newLeafNew)),
								new PMTBranch.PMTBranchChild(remainderOld.getFirstNibble(), represent(newLeafOld))
						);
						acc = computeAndSetTip(commonPath, newBranch, acc);
						acc.add(newLeafNew, newLeafOld, newBranch);
						acc.mark(current);
						break;
					case NONE:
						if (val == current.getValue()) {
							throw new IllegalArgumentException("Nothing changed");
						} else {
							// INFO: we preserve whole key-end as there are no branches
							newLeaf = new PMTLeaf(key, val);
							acc.setTip(newLeaf);
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
						acc.setTip(modifiedBranch);
						acc.add(modifiedBranch);
						acc.mark(current);
						break;
					case NEW:
						var nextHash = currentBranch.getNextHash(key);
						PMTNode subTip;
						if (nextHash == null || nextHash.length == 0) {
							var newLeaf = new PMTLeaf(key.getTailNibbles(), val);
							acc.add(newLeaf);
							subTip = newLeaf;
						} else {
							var nextNode = read(nextHash);
							acc = insertNode(nextNode, key.getTailNibbles(), val, acc);
							subTip = acc.getTip();
						}
						var branchWithNext = cloneBranch(currentBranch);
						branchWithNext.setNibble(new PMTBranch.PMTBranchChild(key.getFirstNibble(), represent(subTip)));
						acc.setTip(branchWithNext);
						acc.add(branchWithNext);
						acc.mark(current);
						break;
				}
				break;
			case EXTENSION:
				switch (commonPath.whichRemainderIsLeft()) {
					case OLD:
						var remainder = commonPath.getRemainder(PMTPath.Subtree.OLD);
						byte[] sliceVal;
						if (remainder.isNibble()) {
							sliceVal = current.getValue();
						} else {
							var newShorter = new PMTExt(remainder.getTailNibbles(), current.getValue());
							acc.add(newShorter);
							sliceVal = represent(newShorter);
						}
						var newBranch = new PMTBranch(
							val,
							new PMTBranch.PMTBranchChild(remainder.getFirstNibble(), sliceVal)
						);
						acc = computeAndSetTip(commonPath, newBranch, acc);
						acc.add(newBranch);
						acc.mark(current);
						break;
					case NEW:
					case NONE:
						var nextHash = current.getValue();
						var nextNode = read(nextHash);
						// INFO: for NONE, the NEW will be empty
						acc = insertNode(nextNode, commonPath.getRemainder(PMTPath.Subtree.NEW), val, acc);
						var subTip = acc.getTip();
						// INFO: for NEW or NONE, the commonPrefix can't be null as it existed here
						var newExt = new PMTExt(commonPath.getCommonPrefix(), represent(subTip));
						acc.setTip(newExt);
						acc.add(newExt);
						acc.mark(current);
						break;
					case BOTH:
						var remainderNew = commonPath.getRemainder(PMTPath.Subtree.NEW);
						var remainderOld = commonPath.getRemainder(PMTPath.Subtree.OLD);
						// INFO: as implemented here, the key-end can be empty
						var newLeaf = new PMTLeaf(remainderNew.getTailNibbles(), val);
						if (remainderOld.isNibble()) {
							sliceVal = current.getValue();
						} else {
							var newShorter = new PMTExt(remainderOld.getTailNibbles(), current.getValue());
							acc.add(newShorter);
							sliceVal = represent(newShorter);
						}
						newBranch = new PMTBranch(
							null,
							new PMTBranch.PMTBranchChild(remainderNew.getFirstNibble(), represent(newLeaf)),
							new PMTBranch.PMTBranchChild(remainderOld.getFirstNibble(), sliceVal)
						);
						acc = computeAndSetTip(commonPath, newBranch, acc);
						acc.add(newBranch, newLeaf);
						acc.mark(current);
						break;
				}
				break;
		}
		return acc;
	}

	PMTAcc computeAndSetTip(PMTPath pmtPath, PMTBranch branch, PMTAcc acc) {
		if (pmtPath.getCommonPrefix().isEmpty()) {
			acc.setTip(branch);
			return acc;
		} else {
			var newExt = new PMTExt(pmtPath.getCommonPrefix(), represent(branch));
			acc.setTip(newExt);
			acc.add(newExt);
			return acc;
		}
	}

	PMTBranch cloneBranch(PMTBranch currentBranch) {
		return currentBranch.copyForEdit();
	}

	private PMTNode read(byte[] key) {
		return this.cache.get(key);
	}

	private PMTNode nodeLoader(byte[] key) {
		byte[] node;
		if (hasDbRepresentation(key)) {
			node = db.read(key);
		} else {
			node = key;
		}
		return PMTNode.deserialize(node);
	}

	private byte[] hash(byte[] serialized) {
		return this.hashFunction.hash(serialized);
	}

	private byte[] hash(PMTNode node) {
		return hash(node.serialize());
	}

}
