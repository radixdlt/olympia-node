package com.radixdlt.store.tree;

import com.radixdlt.atom.SubstateId;

public class SubStateTree {

	// How to re-use existing up/down?
	public enum Value  {
		UP,
		DOWN
	}

	private PMT pmt;

	public SubStateTree() {
		var storage = new PMTCachedStorage();
		pmt = new PMT(storage);
	}

	public Boolean put(SubstateId key, Value value) {
		var val = new byte[1];

		// Proposal for value structure:
		// for DOWN: 1 + TxId + Tokens
		// for UP: 0 + Tokens

		switch (value) {
			case UP: val[0] = 0; break;
			case DOWN: val[0] = 1; break;
		}

		var root = pmt.add(key.asBytes(), val);

		return true;
	}

}
