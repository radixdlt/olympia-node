package com.radixdlt.store.tree;

public interface PMTStorage {
	void save(byte[] serialisedNodeHash, byte[] serialisedNode);
	byte[] read(byte[] serialisedNodeHash);
}
