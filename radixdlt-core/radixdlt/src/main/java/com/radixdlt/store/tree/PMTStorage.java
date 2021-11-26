package com.radixdlt.store.tree;

import java.util.List;

public interface PMTStorage {
	void save(PMTNode node);
	void save(List<PMTNode> node);
	PMTNode read(byte[] hash);
}
