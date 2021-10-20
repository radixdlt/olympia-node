package com.radixdlt.store.tree;

import java.util.HashMap;

public class PMTPath {

	enum Subtree {
		OLD,
		NEW,
		BOTH,
		NONE
	}

	private PMTNode tip;
	private PMTKey commonPrefix;
	private HashMap<Subtree, PMTKey> rem = new HashMap<>();
	private Subtree suffix;


	// handle null in rem?
	public PMTKey getRemainder(Subtree subtree) {
		return rem.get(subtree);
	}

	public PMTKey getCommonPrefix() {
		return commonPrefix;
	}

	public PMTPath setTip(PMTNode newTip) {
		tip = newTip;
		return this;
	}

	public PMTNode getTip () {
		return this.tip;   /// TODO: trigger cleanup for recursive calls?
	}

	// INFO: Branch has empty key and remainder
	public PMTPath setRemainder(PMTKey existing, PMTKey incoming, PMTKey common) {

		this.rem.put(Subtree.OLD, existing);
		this.rem.put(Subtree.NEW, incoming);

		this.commonPrefix = common;
		recogniseRemainder(existing, incoming);

		return this;
	}

	public PMTPath recogniseRemainder(PMTKey existing, PMTKey incoming) {
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

	public PMTPath cleanup() {
		this.commonPrefix = null;
		this.rem = null;
		this.suffix = null;
		return this;
	}
}
