package com.radixdlt.store.tree;

import java.util.Arrays;
import java.util.List;

public class PMTAcc {

	private List<PMTNode> visitedAcc;
	private List<PMTNode> addedAcc;

	public PMTAcc() { }

	private PMTNode newTip;

	//INFO: it can be Leaf or Branch
	private PMTNode retVal;

	private Boolean notFound;

	public void setNewTip(PMTNode newTip) {
		this.newTip = newTip;
	}

	public PMTNode getTip() {
		return newTip;
	}

	public void setRetVal(PMTNode ret) {
		this.retVal = ret;
	}

	public PMTNode getRetVal() {
		return this.retVal;
	}

	public void setNotFound() {
		this.notFound = true;
	}

	public Boolean notFound() {
		return this.notFound;
	}


	public List<PMTNode> getNewNodes() {
		return addedAcc;
	}

	// handle nulls that got added (when Ext was null) or handle them at insertion
	public void add(PMTNode... nodes) {
		if (addedAcc != null) {
			addedAcc.addAll(Arrays.stream(nodes).toList());
		} else {
			addedAcc = Arrays.stream(nodes).toList();
		}
	}

	public void mark(PMTNode node) {
		if (visitedAcc != null) {
			visitedAcc.add(node);
		} else {
			visitedAcc = List.of(node);
		}
	}

}
