package com.radixdlt.store.tree;

import com.radixdlt.atom.SubstateId;

public class StateTree {

	// How to re-use existing up/down?
	public enum Value  {
		UP,
		DOWN
	}

	private PMT pmt;

	public StateTree() {
		pmt = new PMT();
	}

	public Boolean put(SubstateId key, Value value) {
		var val = new byte[1];

		switch (value) {
			case UP: val[0] = 0;
			case DOWN: val[0] = 1;
		}

		var root = pmt.add(key.asBytes(), val);

		return true;
	}

}
