package com.radixdlt.store.tree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PMTAcc {

	private List<PMTNode> oldAcc;
	private List<PMTNode> newAcc;

	public PMTAcc() {}

	public PMTNode newTip;

	public PMTNode getTip() {
		return newTip;
	}

	public List<PMTNode> getNewNodes() {
		return newAcc;
	}

	// handle nulls that got added (when Ext was null) or handle them at insertion
	public void add(PMTNode... nodes) {
		if (newAcc != null) {
			newAcc.addAll(Arrays.stream(nodes).toList());
		} else {
			newAcc = Arrays.stream(nodes).toList();
		}
	}

	public void mark(PMTNode node) {
		if (oldAcc != null) {
			oldAcc.add(node);
		} else {
			oldAcc = List.of(node);
		}
	}

}
