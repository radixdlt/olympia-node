package com.radixdlt.store.tree;

import com.radixdlt.atom.Substate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PMTResult {

	private byte[] tip;
	private PMTKey commonPrefix;
	private HashMap<Subtree, PMTKey> rem = new HashMap<>();

	private Subtree suffix;

	enum Subtree {
		OLD,
		NEW,
		BOTH,
		NONE
	}

	// handle null in rem?
	public PMTKey getRemainder(Subtree subtree) {
		return rem.get(subtree);
	}

	public PMTKey getCommonPrefix() {
		return commonPrefix;
	}

	public PMTResult setTip(byte[] newTip) {
		tip = newTip;
		return this;
	}

	// what about concurrent access?
	public PMTResult setRemainder(PMTKey existing, PMTKey incoming, PMTKey common) {

		this.rem.put(Subtree.OLD, existing);
		this.rem.put(Subtree.NEW, incoming);

		this.commonPrefix = common;
		recogniseRemainder(existing, incoming);

		return this;
	}

	public PMTResult recogniseRemainder(PMTKey existing, PMTKey incoming) {
		if (existing.isEmpty()) {
			if (incoming.isEmpty()) {
				this.suffix = Subtree.NONE;
			} else {
				this.suffix = Subtree.NEW;
			}
		} else {
			if (incoming.isEmpty()) {
				this.suffix = Subtree.OLD;
			} else {
				this.suffix = Subtree.BOTH;
			}
		}
		return this;
	}

	public Subtree whichRemainderIsLeft() {
		return this.suffix;
	}

}
