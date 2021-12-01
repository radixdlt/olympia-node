package com.radixdlt.store.tree;

import java.util.HashMap;

public class InMemoryPMTStorage implements PMTStorage {

	private HashMap<byte[], byte[]> localDb;             // mock for final persistent DB API

	public InMemoryPMTStorage() {
		localDb = new HashMap<>();
	}

	public void save(byte[] serialisedNodeHash, byte[] serialisedNode) {
		// TODO: introduce better key for local cache to enable removal (e.g. with round number)
		this.localDb.put(serialisedNodeHash, serialisedNode);
	}

	public byte[] read(byte[] serialisedNodeHash) {
		return this.localDb.get(serialisedNodeHash);
	}
}


