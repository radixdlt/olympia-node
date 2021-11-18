package com.radixdlt.store.tree;

import java.util.HashMap;

public class PMTCachedStorage implements PMTStorage {


	private HashMap<byte[], byte[]> localDb;             // mock for final persistent DB API
	private HashMap<byte[], PMTNode> localCache;         // mock for cache

	public PMTCachedStorage() {
		localDb = new HashMap<>();
		localCache = new HashMap<>();
	}

	public void save(PMTNode node) {
		// TODO: introduce better key for local cache to enable removal (e.g. with round number)
		this.localCache.put(node.getHash(), node);
		var ser = node.serialize();
		if (ser.length >= PMTNode.DB_SIZE_COND) {
			this.localDb.put(node.getHash(), ser);
		}
	}

	public PMTNode read(byte[] hash) {
		var localNode = localCache.get(hash);
		if (localCache.get(hash) != null) {
			return localNode;
		} else {
			if (hash.length < PMTNode.DB_SIZE_COND) {
				return PMTNode.deserialize(hash);
			} else {
				var node = this.localDb.get(hash);
				return PMTNode.deserialize(node);
			}
		}
	}

}


