package com.radixdlt.store.tree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PMTAcc {

	// TODO maybe make this class immutable?
	private List<PMTNode> visitedAcc = new ArrayList<>();;
	private List<PMTNode> addedAcc = new ArrayList<>();

	public PMTAcc() { }

	private PMTNode tip;
	//INFO: it can be Leaf or Branch (as these are the only terminating nodes in PMT algo)
	private PMTNode retVal;
	private boolean notFound;

	public void setTip(PMTNode newTip) {
		this.tip = newTip;
	}
	public PMTNode getTip() {
		return tip;
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

	// TODO try to improve this
	public boolean notFound() {
		return this.notFound;
	}


	public List<PMTNode> getNewNodes() {
		return addedAcc;
	}

	public void add(PMTNode... nodes) {
		addedAcc.addAll(Arrays.stream(nodes).toList());
	}

	public void mark(PMTNode node) {
		visitedAcc.add(node);
	}

}
