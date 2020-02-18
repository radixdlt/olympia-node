package com.radixdlt.mempool;

import com.radixdlt.common.Atom;

/**
 * Exception thrown when mempool rejects an atom.
 */
public class MempoolRejectedException extends Exception {

	private final Atom atom;

	public MempoolRejectedException(Atom atom, String message) {
		super(message);
		this.atom = atom;
	}

	public Atom atom() {
		return atom;
	}
}
