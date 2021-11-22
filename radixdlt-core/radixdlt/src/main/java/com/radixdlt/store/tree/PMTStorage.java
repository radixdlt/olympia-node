package com.radixdlt.store.tree;

import java.util.List;

public interface PMTStorage {
		public void save(PMTNode node);
		public void save(List<PMTNode> node);
		public PMTNode read(byte[] hash);
}
