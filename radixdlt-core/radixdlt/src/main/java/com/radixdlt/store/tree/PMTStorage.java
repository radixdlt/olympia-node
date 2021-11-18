package com.radixdlt.store.tree;

public interface PMTStorage {
		public void save(PMTNode node);
		public PMTNode read(byte[] hash);
}
