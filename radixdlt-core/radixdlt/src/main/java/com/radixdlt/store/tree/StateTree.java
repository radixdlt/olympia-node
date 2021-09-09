package com.radixdlt.store.tree;

import com.google.inject.Inject;
import com.radixdlt.atom.SubstateId;


// top level access class with substateId and value
// lower level byte[] key, val

public class StateTree {

	// How to re-use existing up/down?
	public enum Value  {
		UP,
		DONW
	}

	private PMT pmt;
	private byte[] root;

	public StateTree() {
		pmt = new PMT();
	}

	// TODO: interface?
	//put, UP = put
	//put, DOWN = update
	//get
	//get_root
	//get_proof
	//verify_proof
	//
	//? no delete, we can fetch old tree @root
	// StateTree() - new
	// StateTree(Root) - existing in db

	public byte[] put(SubstateId key, Value value) {
		var val = new byte[1];

		switch (value) {
			case UP: val[0] = 0;
			case DONW: val[0] = 1;
		}

		return pmt.put(key.asBytes(), val);
	}

}
