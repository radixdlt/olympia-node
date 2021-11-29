package com.radixdlt.store.tree;

import org.spongycastle.util.encoders.Hex;

import java.util.HashMap;
import java.util.List;

public class PMTCachedStorage implements PMTStorage {


	private HashMap<String, byte[]> localDb;             // mock for final persistent DB API
	private HashMap<String, PMTNode> localCache;         // mock for cache

	public PMTCachedStorage() {
		localDb = new HashMap<>();
		localCache = new HashMap<>();
	}

	public void save(List<PMTNode> nodes) {
		nodes.stream().forEach(n -> save(n));
	}

	public void save(PMTNode node) {
		// TODO: introduce better key for local cache to enable removal (e.g. with round number)
		this.localCache.put(Hex.toHexString(node.getHash()), node);
		var ser = node.serialize();
		if (ser.length >= PMTNode.DB_SIZE_COND) {
			this.localDb.put(Hex.toHexString(node.getHash()), ser);
		}
	}

	public PMTNode read(byte[] hash) {
		var localNode = localCache.get(Hex.toHexString(hash));
		if (localCache.get(Hex.toHexString(hash)) != null) {
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


